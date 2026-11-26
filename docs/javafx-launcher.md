# Launch Coffee Mongers with one command

From the repository root on Windows:

```powershell
.\run-javafx.ps1
```

The launcher checks for JDK 26 (`java` and `javac`), Maven and Node.js 22.20+,
loads the existing database configuration, and runs `mvn compile javafx:run`.
Compilation happens before opening the window, so source changes are included.
It does not run the test suite on each launch; run `mvn test` separately.
JavaFX and the Java backend run in one application. PostgreSQL must already be
available locally or through the existing Neon connection. No database service
is started, created, reset, migrated or seeded by the launcher.

## Options

| Command | Result |
| --- | --- |
| `.\run-javafx.ps1` | Load configured database settings and open JavaFX. |
| `.\run-javafx.ps1 -Check` | Check tools and parse settings without opening a window or connecting to PostgreSQL. |
| `.\run-javafx.ps1 -Demo` | Open the session-only demo without loading database files or enabling test identities. |
| `.\run-javafx.ps1 -Demo -Check` | Check the demo launch configuration. |
| `.\run-javafx.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.2.1'` | Choose an installed JDK explicitly. Replace the example directory as needed. |
| `.\run-javafx.ps1 -Offline` | Use cached Maven dependencies; the first launch normally needs downloads. |
| `npm.cmd run javafx` | Equivalent Windows launcher through npm. |

The script uses its own location as the project root, so an absolute script path
can be called from another directory. It restores the caller's `JAVA_HOME` and
`PATH` when the app exits or a launch fails. Explicit `-JavaHome` wins; otherwise
it uses `JAVA_HOME`, an installed JDK 26 under `Program Files\Java`, or PATH.
Both the compiler and runtime must report version 26. Maven must be on PATH.
Node is used for the existing environment adapter; `npm ci` is not required
just to run this launcher.

If PowerShell blocks script execution, use `npm.cmd run javafx` or this
invocation-specific command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-javafx.ps1
```

An organizational policy can still restrict script execution. The launcher does
not change the machine's execution policy.

## Database settings and accounts

Selection follows the existing `scripts/database-env.mjs` adapter:

1. Process `DATABASE_URL`, if present.
2. Otherwise the process `DB_URL`, `DB_USER`, `DB_PASSWORD` combination.
3. Otherwise `.env` if it exists, or `.env.local`.

The URL adapter maps the Postgres URI into the Java JDBC variables. Explicit
process database settings win as a complete configuration; in that case the
file loader is not used, so put the desired `COFFEE_TEST_*` settings in the same
terminal too. When the selected file supplies the database, its other settings
are passed through and explicit terminal values take precedence. Invalid
database settings fail before Maven; there is no silent fallback to Demo.
Connection strings and passwords are not included in launcher status messages
or Maven command arguments.

Use the existing test-account settings for PostgreSQL-backed workspaces:

- Buyer: `COFFEE_TEST_SHOP_ID` and `COFFEE_TEST_BUYER_PASSWORD`.
- Supplier: `COFFEE_TEST_SUPPLIER_ID` and `COFFEE_TEST_SUPPLIER_PASSWORD`.
- Admin: `COFFEE_TEST_ADMIN_PASSWORD`.

The launcher does not generate accounts or change the mapped business IDs.
Sign in as `buyer@coffeemongers.test`, `supplier@coffeemongers.test` or
`admin@coffeemongers.test`, using the configured password. Ordinary registered
accounts and `admin@coffeemongers.demo` remain in-memory demo accounts. Restart
after changing identity settings. The standard password/active-ID rules still
apply inside the application.

For a database-free preview, `-Demo` ignores `.env`/`.env.local` and removes
database variables and `COFFEE_TEST_*` only from the child environment. Existing
terminal variables and local files remain intact. The built-in demo Admin is
`admin@coffeemongers.demo` / `CoffeeAdmin2026!`.

`-Check` verifies configuration parsing and tool versions, not database reachability,
schema, password correctness or business-record existence. The UI reports
database failures when you open the configured workspace. No full live-data
workflow is implied by a successful configuration check.

## Checks

```powershell
npm.cmd test
.\run-javafx.ps1 -Demo -Check
.\run-javafx.ps1 -Check
```

The Node tests cover both the existing environment adapter and the new launcher:
JDBC/URI/file configuration, process precedence, configured test identities,
explicit demo isolation, check-only behavior, redacted errors, Maven failures
and fixed Windows/offline argument handling. Existing console and Swing commands
retain their previous meanings: `npm start` is console, `npm run desktop` is Swing.

Direct `mvn javafx:run` remains available and still expects Java's JDBC environment
variables; Maven by itself does not load `.env.local`. This launcher changes only
startup and configuration plumbing, not Java services or database rules.
