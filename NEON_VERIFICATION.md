# Neon verification — 2026-10-27

App: `C:\Users\MY PC\Desktop\java` (Java 26 / Maven console application).

Organization: `org-shiny-rain-78483303`; project: `flat-credit-48009839`; branch: `production`.

## Changes

- Installed the latest Neon CLI (8.13.0) and ran the requested `neon init -y --org-id org-shiny-rain-78483303 --project-id flat-credit-48009839 --branch production --services postgres`.
- Added the CLI-generated `neon.ts`, `.neon` context, npm dependencies and lockfile, and `.env.local` containing the production branch's managed variables. Context and credentials are ignored by Git.
- Added npm `build`, `start`, `test`, and `test:neon` commands. The build packages the existing Maven app and copies its runtime dependencies.
- Added a launcher that reads Neon environment variables and translates `DATABASE_URL` into the existing JDBC `DB_URL`, `DB_USER`, `DB_PASSWORD` configuration, with runtime precedence, URL decoding, TLS, and channel binding. The existing PostgreSQL JDBC 42.7.14 driver remains in use.
- Added 11 configuration tests, a live JDBC/DAO smoke test, `.env.example`, `.gitignore`, and `NEON.md`.
- Applied the existing `database/01_schema.sql` to the initially empty production database after validating it on an isolated temporary Neon branch. Production contains the 13-table schema without demo records.

## Observed command output

`neon deploy` — exit 0:

```text
Applying to branch production (br-falling-wind-a1wevsdg)
No changes — branch production already matches the policy.
Utilized services: Postgres
Pulled 3 Neon variables into .env.local
  Postgres        DATABASE_URL, DATABASE_URL_UNPOOLED
  Branch          NEON_BRANCH
```

`npm test` — exit 0:

```text
tests 11
pass 11
fail 0
```

`npm run build` — exit 0 with the finalized Windows launcher:

```text
> coffee-supplier-allocation@1.0.0 build
> node scripts/java.mjs build
[INFO] BUILD SUCCESS
[INFO] Total time:  9.955 s
```

Schema/constraint test on the isolated `setup-verify-20261027` branch — exit 0:

```text
PASS: 13 tables, valid flow, candidate query, 18 rejection cases, ISO boundary and rollback.
ROLLBACK
```

The demo seed ran only on that test branch. `npm run test:neon` there — exit 0:

```text
PASS: JDBC connection is valid
PASS: JDBC requires TLS
PASS: JDBC requires channel binding
PASS: All 13 application tables exist; missing=[]
PASS: app DAO reads (products=5, shops=5, demand items=7, supplies=3)
PASS: Postgres write/read transaction
PASS: Probe transaction rolled back
PASS: Neon Postgres end-to-end smoke test
```

The installed PostgreSQL client independently reported `SSL Connection = true` and `SSL Protocol = TLSv1.3` on the test branch.

`npm start` was run with input exercising menu options 1–5 on that branch. Observed output included:

```text
Total active products: 5
Total available: 90.00 kg
Total open demand items: 7
Total active coffee shops: 5
Save this demand? [y/N]: Demand saved. ID: 11
Total open demand items: 8
Choose: Goodbye!
PASS: console menu options 1-5, committed demand creation, and independent database verification on isolated Neon test branch.
```

An independent SQL query verified exactly one persisted demand item for the console-created demand ID, shop, product, OPEN status, and quantity 1.25.

After applying the verified schema to production, `npm run test:neon` used the production `.env.local` directly — exit 0:

```text
PASS: JDBC connection is valid
PASS: JDBC requires TLS
PASS: JDBC requires channel binding
PASS: All 13 application tables exist; missing=[]
PASS: app DAO reads (products=0, shops=0, demand items=0, supplies=0)
PASS: Postgres write/read transaction
PASS: Probe transaction rolled back
PASS: Neon Postgres end-to-end smoke test
```

`npm start` then ran the actual console's read menu options against production and exited normally:

```text
Total active products: 0
No active products found.
Total open demand items: 0
Total active coffee shops: 0
Choose: Goodbye!
PASS: production console startup and read paths using .env.local; production contains the schema without demo data.
```

The Java application runs locally with `npm start`; Neon hosts its Postgres service.

The temporary test branch `setup-verify-20261027` (`br-red-cake-a1v0tpjw`) was deleted after verification. Production remains the linked local branch.

## Failures and recovered issues

- The first deployment's browser sign-in timed out after 60 seconds. The user completed `neon login`; the deployment retry succeeded.
- The initial smoke test incorrectly used backend `pg_stat_ssl` as a client TLS assertion and failed. Neon terminates client TLS at its proxy. The test now checks the live JDBC connection's enforced TLS/channel-binding requirements; an independent client connection confirmed TLS 1.3. Subsequent app and smoke checks passed.
- The CLI's embedded `neon psql` fallback did not produce usable query output here. Validation used the installed PostgreSQL 18 client instead.
- Global npm installation warned that esbuild's postinstall script was not allowed. CLI initialization and deployment nevertheless succeeded. The CLI also emitted a Node shell deprecation warning during initialization. The app launcher's corresponding warning was fixed before final verification.

No unresolved build, deployment, database, or app startup failure remains. Real catalog/shop/supply data has not been loaded into production.
