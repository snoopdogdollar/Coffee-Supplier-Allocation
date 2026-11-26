# Neon connection setup for the Java desktop app

Updated **2026-11-26** from the repository's launchers and configuration adapter.
The application remains Java 26/Maven with pgJDBC 42.7.14. Node.js 22.20+ provides
launcher/config tooling; it is not a separate database driver or backend server.
See [README.md](README.md) for prerequisites and [the launcher guide](docs/javafx-launcher.md)
for complete Windows setup.

## Start the existing application

If the selected database already contains the application schema, keep it and
prepare the test-account settings before launch:

```powershell
.\run-javafx.ps1 -Check
.\run-javafx.ps1
```

The launcher compiles and opens JavaFX and the Java backend in the same process.
It connects to the configured database; it does not start PostgreSQL, provision
Neon, deploy configuration, apply schema, seed or create login accounts.
`-Check` parses settings/checks local tools; it does not test database reachability,
credentials, schema or mapped IDs. `-Demo` is the separate database-free mode.

| Command | Purpose |
| --- | --- |
| `npm.cmd run javafx` | The Windows JavaFX launcher above. |
| `npm.cmd run build` | Maven packaging/tests and runtime dependency copying. |
| `npm.cmd start` | Console, with delivery week selection in menu 13. |
| `npm.cmd run desktop` | Swing, not JavaFX. |
| `npm.cmd test` | Node environment-adapter and JavaFX-launcher checks. |
| `mvn test` | Java tests; PostgreSQL fixtures are separately opt-in. |
| `npm.cmd run test:neon` | Optional database smoke check, described below. |

`npm.cmd ci` installs the locked Node packages when using the repository's Neon
tooling; it is not needed merely to use the JavaFX helper's Node built-ins.

## Connection settings and precedence

The launchers use `scripts/database-env.mjs`. Selection order is:

1. Process `DATABASE_URL`.
2. Otherwise a complete process `DB_URL`, `DB_USER`, `DB_PASSWORD` combination.
3. Otherwise `.env` if it exists, or `.env.local`.

The selected process configuration wins as a complete set; file loading is skipped
in that case. Set the desired `COFFEE_TEST_*` identities in that terminal as well.
If the file supplies database settings, its extra settings are passed through,
with explicit terminal values taking precedence.

The adapter converts the Postgres URI to the existing JDBC triple, decodes URL
credentials, preserves connection options and maps `channel_binding` to
`channelBinding`. Its URL path requires TLS (sslmode require or stricter) and
channel binding require. For a local database without those transport settings,
use the JDBC triple instead. Launcher status/errors do not print credentials.

`DATABASE_URL` is the URI selected by the adapter. The separate
`DATABASE_URL_UNPOOLED` is for direct-connection administration/migrations, not an
automatic fallback database in the launcher. `NEON_BRANCH` is metadata and does
not itself select a JDBC connection.

Direct `mvn javafx:run` or `java` does not load dotenv/convert the URI; it uses the
original `DB_URL`, `DB_USER`, `DB_PASSWORD` variables. Do not confuse loading a file
with successful database authentication.

## Test identities

Database-backed JavaFX requires explicitly configured accounts:

- Buyer: `COFFEE_TEST_SHOP_ID` and `COFFEE_TEST_BUYER_PASSWORD`.
- Supplier: `COFFEE_TEST_SUPPLIER_ID` and `COFFEE_TEST_SUPPLIER_PASSWORD`.
- Admin: `COFFEE_TEST_ADMIN_PASSWORD`.

Use the corresponding `@coffeemongers.test` logins documented in README. IDs map
to real business records; creating a shop/supplier does not create a login.
Passwords and IDs are read at startup. Registration and the built-in demo Admin
remain session-only demo identities. No production identity provider was added.

## Existing Neon context and schema administration

`neon.ts` is the repository configuration; `.neon` is ignored local context. Use the
project/branch actually selected for the intended task. Do not copy an organization,
project or production branch identifier from an old verification report into a new
setup. Ordinary application launch does not require a new deployment.

[NEON_VERIFICATION.md](NEON_VERIFICATION.md) records the earlier setup/deployment
and its checks. Those commands, identifiers and outcomes are historical evidence,
not instructions to reapply infrastructure against the current database.

The schema source is [database/01_schema.sql](database/01_schema.sql). For new
synthetic data, use an isolated empty demo database and choose either the fixed
historical seed or the rolling seed, following [database/README.md](database/README.md).
Test any later schema migration in isolation before applying it to application data;
use the intended direct administration connection for that operation.

Do not commit `.env`, `.env.local`, `.neon` or credentials. `.env.example` contains
placeholders; preserve existing local configuration instead of overwriting it.

## Optional smoke check and evidence scope

After building/configuring an isolated test target, `npm.cmd run test:neon` uses
the existing Java connection class/DAOs. It checks connection requirements, the
13-table baseline, catalog/demand/supply reads and a temporary write/read followed
by rollback. It does not seed demo data. It accesses the configured database and
is not an offline unit test; rollback does not imply identity sequences cannot advance.

The historical report explains its client TLS/channel-binding checks; a proxy-side
`pg_stat_ssl` observation alone is not used there to establish client encryption.

No Neon API, deployment, smoke check or live database verification was run for this
documentation update. Consult the dated report for the scope of its actual evidence.
