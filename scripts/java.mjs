import { spawnSync } from 'node:child_process';
import { delimiter, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseConfigurationError, loadDatabaseEnvironment } from './database-env.mjs';

const command = process.argv[2];
const root = process.cwd();
const classpath = [join(root, 'target', 'classes'), join(root, 'target', 'dependency', '*')].join(delimiter);

function run(executable, arguments_, options = {}) {
  const result = spawnSync(executable, arguments_, {
    cwd: root,
    stdio: 'inherit',
    ...options,
  });
  if (result.error) {
    console.error(`Unable to start ${executable}. Check that it is installed and available on PATH.`);
    process.exitCode = 1;
  } else {
    process.exitCode = result.status ?? 1;
  }
}

try {
  if (command === 'build') {
    const windows = process.platform === 'win32';
    if (windows) {
      // Batch files need cmd.exe. The command is fixed and accepts no user input.
      run(process.env.ComSpec || 'cmd.exe', [
        '/d', '/s', '/c',
        'mvn.cmd --batch-mode package dependency:copy-dependencies -DincludeScope=runtime',
      ]);
    } else {
      run('mvn', [
        '--batch-mode', 'package', 'dependency:copy-dependencies', '-DincludeScope=runtime',
      ]);
    }
  } else if (command === 'start' || command === 'smoke') {
    const target = command === 'start'
      ? 'com.coffeeallocation.Main'
      : fileURLToPath(new URL('./NeonSmokeTest.java', import.meta.url));
    run('java', ['-cp', classpath, target], {
      env: loadDatabaseEnvironment(root),
    });
  } else {
    console.error('Usage: node scripts/java.mjs build|start|smoke');
    process.exitCode = 1;
  }
} catch (error) {
  // Adapter errors contain field names only, never a connection string.
  console.error(error instanceof DatabaseConfigurationError
    ? error.message
    : 'Unable to launch Java. Check database configuration and the installed toolchain.');
  process.exitCode = 1;
}
