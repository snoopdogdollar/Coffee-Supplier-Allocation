# Neon setup

The app remains Java 26 with Maven and PostgreSQL JDBC. Node 22.20+ runs the Neon CLI and the small npm launcher; there is no Node database driver or server.

## Commands

```powershell
npm ci
npm test
npm run build
npm start
npm run test:neon
```

`build` packages the existing Maven app and copies its runtime dependency jars into `target/dependency`. `start` runs the existing console menu. `test:neon` uses the app's connection class and DAOs, checks that the live JDBC connection requires TLS and channel binding, checks the 13 application tables, reads catalog/demand/supply data, and verifies a temporary write/read transaction followed by rollback. It does not populate the production database with demo data. `pg_stat_ssl` is not used to infer client encryption because Neon terminates client TLS at its proxy.

## Connection settings

`neon deploy` applies `neon.ts` to the project and branch recorded in the ignored `.neon` file. For this setup: organization `org-shiny-rain-78483303`, project `flat-credit-48009839`, branch `production`. It pulls `DATABASE_URL`, `DATABASE_URL_UNPOOLED`, and `NEON_BRANCH` into `.env.local` (or `.env` when that file exists).

The npm launcher loads the existing `.env`, otherwise `.env.local`. Explicit process configuration takes precedence over file configuration: process `DATABASE_URL`, then a process legacy `DB_URL` configuration, then file settings. The launcher maps the selected `DATABASE_URL` to JDBC `DB_URL`, `DB_USER`, and `DB_PASSWORD` for the existing Java connection code. It decodes URL credentials, preserves SSL settings, and maps `channel_binding` to the JDBC option `channelBinding`. Connection strings and passwords are never printed by the launcher.

Keep `sslmode=require` or stricter in Neon URLs. Use `DATABASE_URL` for the application's normal pooled traffic. Use `DATABASE_URL_UNPOOLED` for migrations and session-dependent administration. Java launched directly without npm continues to use its original `DB_URL`, `DB_USER`, and `DB_PASSWORD` environment variables.

Database schema source remains `database/01_schema.sql`; it creates the 13-table baseline once on an empty database. Test schema changes on an isolated Neon branch before applying them to production. The seed script is for an isolated demo/test database only.

Do not commit `.env`, `.env.local`, `.neon`, or credentials. `.env.example` contains placeholders only.
