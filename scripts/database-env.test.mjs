import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { databaseUrlToJdbcEnv, loadDatabaseEnvironment } from './database-env.mjs';

const example = 'postgresql://demo:sample@ep-example.neon.tech/neondb?sslmode=require&channel_binding=require';

function withDirectory(callback) {
  const directory = mkdtempSync(join(tmpdir(), 'coffee-neon-config-'));
  try {
    callback(directory);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test('converts Neon URLs and translates pgJDBC option names without putting credentials in the JDBC URL', () => {
  const actual = databaseUrlToJdbcEnv(example);
  assert.equal(actual.DB_URL, 'jdbc:postgresql://ep-example.neon.tech/neondb?sslmode=require&channelBinding=require');
  assert.equal(actual.DB_USER, 'demo');
  assert.equal(actual.DB_PASSWORD, 'sample');
  assert.ok(!actual.DB_URL.includes('demo:sample'));
});

test('decodes encoded credentials once and preserves encoded and literal plus signs', () => {
  const actual = databaseUrlToJdbcEnv('postgres://u%3Aser:p%40ss%3Aword%2B%25%2F+%252F@db.example:5432/db%20name');
  assert.equal(actual.DB_USER, 'u:ser');
  assert.equal(actual.DB_PASSWORD, 'p@ss:word+%/+%2F');
  assert.equal(actual.DB_URL, 'jdbc:postgresql://db.example:5432/db%20name?sslmode=require&channelBinding=require');
});

test('maps timeouts and application names while retaining required transport protection', () => {
  const actual = databaseUrlToJdbcEnv(`${example}&connect_timeout=15&application_name=Coffee%20App`);
  assert.match(actual.DB_URL, /connectTimeout=15/);
  assert.match(actual.DB_URL, /ApplicationName=Coffee\+App/);
});

test('preserves literal plus signs in database names and accepts IPv6 hosts', () => {
  const actual = databaseUrlToJdbcEnv('postgresql://demo:sample@[::1]:5432/coffee+demo%2Ftest');
  assert.equal(actual.DB_URL, 'jdbc:postgresql://[::1]:5432/coffee%2Bdemo%2Ftest?sslmode=require&channelBinding=require');
});

test('rejects malformed, incomplete, conflicting, and insecure URLs with redacted errors', () => {
  const values = [
    'private-secret',
    'https://demo:private-secret@db.example/db',
    'postgresql://demo:private-secret@db.example/',
    'postgresql://demo@db.example/db',
    'postgresql://demo:private-secret%ZZ@db.example/db',
    'postgresql://demo:private-secret%00@db.example/db',
    'postgresql://demo:private-secret@db.example/db#fragment',
    'postgresql://demo:private-secret@db.example/db?password=other',
    'postgresql://demo:private-secret@db.example/db?sslmode=disable',
    'postgresql://demo:private-secret@db.example/db?channel_binding=disable',
  ];
  for (const value of values) {
    assert.throws(() => databaseUrlToJdbcEnv(value), (error) => {
      assert.ok(!error.message.includes('private-secret'));
      assert.equal(error.cause, undefined);
      return true;
    });
  }
});

test('runtime DATABASE_URL wins over runtime legacy configuration and dotenv', () => {
  withDirectory((directory) => {
    writeFileSync(join(directory, '.env'), 'DATABASE_URL=invalid-file-value\n');
    const actual = loadDatabaseEnvironment(directory, {
      DATABASE_URL: example,
      DB_URL: 'jdbc:postgresql://localhost/local', DB_USER: 'local', DB_PASSWORD: 'local-pass',
    });
    assert.equal(actual.DB_USER, 'demo');
    assert.equal(actual.DB_PASSWORD, 'sample');
  });
});

test('runtime legacy JDBC configuration still works and wins over dotenv', () => {
  withDirectory((directory) => {
    writeFileSync(join(directory, '.env'), `DATABASE_URL="${example}"\n`);
    const runtime = {
      DB_URL: 'jdbc:postgresql://localhost/local', DB_USER: 'local', DB_PASSWORD: ' literal password ',
      TEST_RUNTIME: 'kept',
    };
    const actual = loadDatabaseEnvironment(directory, runtime);
    assert.deepEqual(actual, runtime);
  });
});

test('reads quoted .env.local values with built-in dotenv parsing and restores caller environment', () => {
  withDirectory((directory) => {
    writeFileSync(join(directory, '.env.local'), `# Neon configuration\n\nDATABASE_URL='${example}'\nCOFFEE_NEON_TEST_ADDED=file-value\n`);
    const before = { ...process.env };
    const actual = loadDatabaseEnvironment(directory, {});
    assert.equal(actual.DB_USER, 'demo');
    assert.equal(actual.DB_PASSWORD, 'sample');
    assert.equal(actual.COFFEE_NEON_TEST_ADDED, 'file-value');
    assert.deepEqual({ ...process.env }, before);
  });
});

test('existing .env takes precedence over .env.local, matching neon deploy', () => {
  withDirectory((directory) => {
    writeFileSync(join(directory, '.env'), `DATABASE_URL="${example}"\n`);
    writeFileSync(join(directory, '.env.local'), 'DATABASE_URL=invalid-stale-value\n');
    assert.equal(loadDatabaseEnvironment(directory, {}).DB_USER, 'demo');
  });
});

test('incomplete or empty runtime configuration fails instead of mixing sources', () => {
  withDirectory((directory) => {
    writeFileSync(join(directory, '.env'), `DATABASE_URL="${example}"\nDB_PASSWORD=file-pass\n`);
    assert.throws(() => loadDatabaseEnvironment(directory, { DB_URL: 'jdbc:postgresql://localhost/local', DB_USER: 'local' }), /requires DB_PASSWORD/);
    assert.throws(() => loadDatabaseEnvironment(directory, { DATABASE_URL: '' }), /DATABASE_URL must/);
  });
});

test('missing configuration returns a useful error without connecting', () => {
  withDirectory((directory) => {
    assert.throws(() => loadDatabaseEnvironment(directory, {}), /Set DATABASE_URL/);
  });
});
