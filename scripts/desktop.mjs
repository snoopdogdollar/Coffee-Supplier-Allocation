import { spawnSync } from 'node:child_process';
import { delimiter, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseConfigurationError, loadDatabaseEnvironment } from './database-env.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));

try {
  const env = loadDatabaseEnvironment(root);
  const windows = process.platform === 'win32';
  const executable = windows
    ? join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe')
    : 'java';
  const args = windows
    ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(root, 'run-desktop.ps1')]
    : ['-cp', [join(root, 'target', 'classes'), join(root, 'target', 'dependency', '*')].join(delimiter),
      'com.coffeeallocation.DesktopMain'];
  const result = spawnSync(executable, args, { cwd: root, env, stdio: 'inherit' });
  if (result.error) {
    console.error('Unable to start the desktop app. Check the installed Java 26 toolchain.');
    process.exitCode = 1;
  } else {
    process.exitCode = result.status ?? 1;
  }
} catch (error) {
  console.error(error instanceof DatabaseConfigurationError
    ? error.message
    : 'Unable to load the database configuration for the desktop app.');
  process.exitCode = 1;
}
