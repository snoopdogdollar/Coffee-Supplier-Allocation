import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { DatabaseConfigurationError, loadDatabaseEnvironment } from './database-env.mjs';

export function javaFxEnvironment(root, runtimeEnv, demo) {
  if (!demo) return loadDatabaseEnvironment(root, runtimeEnv);
  const env = { ...runtimeEnv };
  for (const key of Object.keys(env)) {
    if (/^(DATABASE_URL(?:_UNPOOLED)?|DB_URL|DB_USER|DB_PASSWORD)$/i.test(key)
        || /^COFFEE_TEST_/i.test(key)) delete env[key];
  }
  return env;
}

export function launchJavaFx({
  root = fileURLToPath(new URL('../', import.meta.url)),
  args = process.argv.slice(2),
  runtimeEnv = process.env,
  platform = process.platform,
  run = spawnSync,
  log = console.log,
  error = console.error,
} = {}) {
  try {
    if (args.some(arg => !['--demo', '--check', '--offline'].includes(arg))) {
      throw new Error('Unsupported launcher option.');
    }
    const [major, minor] = process.versions.node.split('.').map(Number);
    if (major < 22 || (major === 22 && minor < 20)) {
      throw new Error('Node.js 22.20 or newer is required.');
    }
    const demo = args.includes('--demo');
    const env = javaFxEnvironment(root, runtimeEnv, demo);
    log(demo ? 'JavaFX demo: session-only accounts and data.' : 'JavaFX: database settings loaded.');
    if (!demo) {
      const roles = [];
      if (env.COFFEE_TEST_SHOP_ID && env.COFFEE_TEST_BUYER_PASSWORD) roles.push('Buyer');
      if (env.COFFEE_TEST_SUPPLIER_ID && env.COFFEE_TEST_SUPPLIER_PASSWORD) roles.push('Supplier');
      if (env.COFFEE_TEST_ADMIN_PASSWORD) roles.push('Admin');
      log(roles.length ? `Configured PostgreSQL test logins: ${roles.join(', ')}.`
        : 'No PostgreSQL test login enabled. Set COFFEE_TEST_* accounts as described in README.md.');
    }
    if (args.includes('--check')) {
      log('Launch configuration checked. No window opened and no database connection tested.');
      return 0;
    }
    log('Compiling and opening Coffee Mongers. Its Java backend runs inside the app.');
    const offline = args.includes('--offline');
    const result = platform === 'win32'
      // This is a fixed command. Credentials/settings travel only through the child environment.
      ? run(env.ComSpec || 'cmd.exe', ['/d', '/s', '/c',
        offline ? 'mvn.cmd -o compile javafx:run' : 'mvn.cmd compile javafx:run'],
      { cwd: root, env, stdio: 'inherit', windowsHide: true, windowsVerbatimArguments: true })
      : run('mvn', [...(offline ? ['-o'] : []), 'compile', 'javafx:run'],
        { cwd: root, env, stdio: 'inherit' });
    if (result.error) {
      error('Unable to start Maven. Check that Maven and JDK 26 are installed and available.');
      return 1;
    }
    return result.status ?? 1;
  } catch (exception) {
    error(exception instanceof DatabaseConfigurationError ? exception.message
      : 'Unable to prepare JavaFX. Check launcher options, Node.js 22.20+, Maven and JDK 26.');
    return 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = launchJavaFx();
}
