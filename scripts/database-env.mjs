import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { loadEnvFile } from 'node:process';

const databaseKeys = ['DATABASE_URL', 'DB_URL', 'DB_USER', 'DB_PASSWORD'];

export class DatabaseConfigurationError extends Error {}

function invalidUrl() {
  // Never attach URL parser errors: their input field can contain credentials.
  return new DatabaseConfigurationError('DATABASE_URL must be a valid Postgres URL with a host, database, username, and password.');
}

/** Convert a Neon URL into the existing Java application's JDBC environment. */
export function databaseUrlToJdbcEnv(value) {
  try {
    if (typeof value !== 'string' || value.trim() === '') throw invalidUrl();
    const url = new URL(value.trim());
    if (!['postgres:', 'postgresql:'].includes(url.protocol)
        || !url.hostname || url.pathname.length < 2
        || !url.username || !url.password || url.hash) {
      throw invalidUrl();
    }

    // URL keeps userinfo escaped; decodeURIComponent preserves literal '+' signs.
    const user = decodeURIComponent(url.username);
    const password = decodeURIComponent(url.password);
    const database = decodeURIComponent(url.pathname.slice(1));
    if ([user, password, database].some((part) => part.includes('\0'))) throw invalidUrl();
    // JDBC treats '+' as a form-encoded space, unlike URI pathname semantics.
    const databasePath = encodeURIComponent(database);
    const parameters = new URLSearchParams();
    for (const [key, optionValue] of url.searchParams) {
      if (['user', 'password'].includes(key.toLowerCase())) throw invalidUrl();
      const jdbcKey = ({
        channel_binding: 'channelBinding',
        connect_timeout: 'connectTimeout',
        application_name: 'ApplicationName',
      })[key] ?? key;
      parameters.set(jdbcKey, optionValue);
    }
    // Neon requires TLS; channel binding is supported by the installed pgJDBC.
    if (!parameters.has('sslmode')) parameters.set('sslmode', 'require');
    if (!parameters.has('channelBinding')) parameters.set('channelBinding', 'require');
    if (!['require', 'verify-ca', 'verify-full'].includes(parameters.get('sslmode'))
        || parameters.get('channelBinding') !== 'require') {
      throw invalidUrl();
    }

    return {
      DB_URL: `jdbc:postgresql://${url.host}/${databasePath}?${parameters.toString()}`,
      DB_USER: user,
      DB_PASSWORD: password,
    };
  } catch {
    throw invalidUrl();
  }
}

function configuredDatabase(environment) {
  if (environment.DATABASE_URL !== undefined) {
    return databaseUrlToJdbcEnv(environment.DATABASE_URL);
  }
  if (environment.DB_URL !== undefined) {
    for (const key of ['DB_URL', 'DB_USER', 'DB_PASSWORD']) {
      if (typeof environment[key] !== 'string' || environment[key].length === 0) {
        throw new DatabaseConfigurationError(`Legacy database configuration requires ${key}.`);
      }
      if (environment[key].includes('\0')) throw new DatabaseConfigurationError(`Legacy database configuration contains an invalid ${key}.`);
    }
    return {
      DB_URL: environment.DB_URL,
      DB_USER: environment.DB_USER,
      DB_PASSWORD: environment.DB_PASSWORD,
    };
  }
  return undefined;
}

function loadSelectedEnvFile(root) {
  const selected = [join(root, '.env'), join(root, '.env.local')].find(existsSync);
  if (!selected) return {};

  const original = { ...process.env };
  try {
    // Read file database values separately so configuration is selected by
    // source, rather than accidentally combining credentials from two sources.
    for (const key of databaseKeys) delete process.env[key];
    loadEnvFile(selected);
    return { ...process.env };
  } catch {
    throw new DatabaseConfigurationError('The selected .env or .env.local file could not be loaded.');
  } finally {
    for (const key of Object.keys(process.env)) {
      if (!(key in original)) delete process.env[key];
    }
    Object.assign(process.env, original);
  }
}

/**
 * Runtime DATABASE_URL wins, followed by the runtime legacy JDBC triple.
 * Otherwise use .env when it exists, or .env.local, matching neon deploy.
 * Returns a child-process environment without modifying the caller's environment.
 */
export function loadDatabaseEnvironment(root = process.cwd(), runtimeEnv = process.env) {
  const runtime = { ...runtimeEnv };
  const runtimeDatabase = configuredDatabase(runtime);
  if (runtimeDatabase) return { ...runtime, ...runtimeDatabase };

  const fromFile = loadSelectedEnvFile(root);
  const fileDatabase = configuredDatabase(fromFile);
  if (!fileDatabase) {
    throw new DatabaseConfigurationError('Set DATABASE_URL or the DB_URL, DB_USER, DB_PASSWORD environment variables.');
  }
  return { ...fromFile, ...runtime, ...fileDatabase };
}
