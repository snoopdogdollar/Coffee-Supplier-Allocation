import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { javaFxEnvironment, launchJavaFx } from './javafx.mjs';

const jdbc = { DB_URL: 'jdbc:postgresql://localhost/coffee', DB_USER: 'tester', DB_PASSWORD: 'private-test-password' };
const url = 'postgresql://tester:private-test-password@db.example/coffee?sslmode=require&channel_binding=require';
function temporary(callback) {
  const root = mkdtempSync(join(tmpdir(), 'coffee-javafx-'));
  try { callback(root); } finally { rmSync(root, { recursive: true, force: true }); }
}
function capture(root, options = {}) {
  const calls = [], messages = [];
  const status = launchJavaFx({ root, runtimeEnv: jdbc, platform: 'win32', args: [],
    run: (...args) => { calls.push(args); return { status: 0 }; },
    log: text => messages.push(text), error: text => messages.push(text), ...options });
  return { status, calls, messages };
}

test('launches JavaFX with the existing JDBC settings, from the repository root', () => temporary(root => {
  const result = capture(root);
  assert.equal(result.status, 0);
  assert.equal(result.calls.length, 1);
  assert.deepEqual(result.calls[0][1], ['/d', '/s', '/c', 'mvn.cmd compile javafx:run']);
  assert.equal(result.calls[0][2].cwd, root);
  assert.equal(result.calls[0][2].env.DB_PASSWORD, jdbc.DB_PASSWORD);
  assert.ok(!JSON.stringify(result.messages).includes(jdbc.DB_PASSWORD));
  assert.ok(!JSON.stringify(result.calls[0][1]).includes(jdbc.DB_PASSWORD));
}));

test('loads DATABASE_URL and configured test accounts from .env.local', () => temporary(root => {
  writeFileSync(join(root, '.env.local'), `DATABASE_URL='${url}'\nCOFFEE_TEST_ADMIN_PASSWORD=LocalAdmin2026!\n`);
  const result = capture(root, { runtimeEnv: {} });
  assert.equal(result.status, 0);
  assert.equal(result.calls[0][2].env.DB_USER, 'tester');
  assert.equal(result.calls[0][2].env.COFFEE_TEST_ADMIN_PASSWORD, 'LocalAdmin2026!');
  assert.match(result.messages.join('\n'), /test logins: Admin/);
  assert.ok(!result.messages.join('\n').includes('LocalAdmin2026!'));
}));

test('preserves explicit process settings and configured role identities', () => temporary(root => {
  writeFileSync(join(root, '.env'), 'DATABASE_URL=stale-invalid-value\n');
  const env = { ...jdbc, COFFEE_TEST_SHOP_ID: '42', COFFEE_TEST_BUYER_PASSWORD: 'Buyer2026!pass' };
  const result = capture(root, { runtimeEnv: env });
  assert.equal(result.status, 0);
  assert.equal(result.calls[0][2].env.COFFEE_TEST_SHOP_ID, '42');
  assert.equal(result.calls[0][2].env.DB_USER, 'tester');
}));

test('demo ignores dotenv and removes database/test accounts only in the child environment', () => temporary(root => {
  writeFileSync(join(root, '.env'), 'DATABASE_URL=invalid\nCOFFEE_TEST_ADMIN_PASSWORD=file-secret\n');
  const original = { ...jdbc, DATABASE_URL: 'invalid', COFFEE_TEST_ADMIN_PASSWORD: 'process-secret', JAVA_HOME: 'jdk26' };
  const env = javaFxEnvironment(root, original, true);
  assert.equal(env.DB_URL, undefined);
  assert.equal(env.COFFEE_TEST_ADMIN_PASSWORD, undefined);
  assert.equal(env.JAVA_HOME, 'jdk26');
  assert.equal(original.DB_PASSWORD, jdbc.DB_PASSWORD);
  assert.equal(capture(root, { runtimeEnv: original, args: ['--demo'] }).status, 0);
}));

test('check validates settings without launching Maven or contacting a database', () => temporary(root => {
  const result = capture(root, { args: ['--check'] });
  assert.equal(result.status, 0);
  assert.equal(result.calls.length, 0);
  assert.match(result.messages.join('\n'), /No window opened/);
}));

test('incomplete or invalid database settings fail before Maven and redact secrets', () => temporary(root => {
  for (const env of [{}, { DB_URL: jdbc.DB_URL }, { DATABASE_URL: 'private-test-password' }]) {
    const result = capture(root, { runtimeEnv: env });
    assert.equal(result.status, 1);
    assert.equal(result.calls.length, 0);
    assert.ok(!result.messages.join('\n').includes('private-test-password'));
  }
}));

test('propagates Maven failure and reports a missing executable without exposing its error', () => temporary(root => {
  assert.equal(capture(root, { run: () => ({ status: 7 }) }).status, 7);
  const result = capture(root, { run: () => ({ error: new Error('private-test-password') }) });
  assert.equal(result.status, 1);
  assert.ok(!result.messages.join('\n').includes('private-test-password'));
}));

test('offline and non-Windows commands keep arguments separate', () => temporary(root => {
  assert.equal(capture(root, { args: ['--offline'] }).calls[0][1][3], 'mvn.cmd -o compile javafx:run');
  const result = capture(root, { platform: 'linux', args: ['--offline'] });
  assert.equal(result.calls[0][0], 'mvn');
  assert.deepEqual(result.calls[0][1], ['-o', 'compile', 'javafx:run']);
  const invalid = capture(root, { args: ['--unexpected'] });
  assert.equal(invalid.status, 1);
  assert.equal(invalid.calls.length, 0);
}));
