# Coffee Supplier Allocation — Coffee Mongers

A Java desktop application that connects coffee shops with suppliers and allocates shared coffee supply by delivery week. Buyers submit demands, an Admin previews and confirms allocations, Suppliers maintain their offerings and weekly supply, and Buyers confirm receipt and review completed orders.

The repository contains a **JavaFX interface**, a **console application**, and a **Swing interface** sharing the Java/JDBC backend. JavaFX also includes an in-memory demo mode. Database access uses PostgreSQL, JDBC and handwritten SQL; there is no ORM or separate web/API server.

## Contents

- [Features and roles](#features-and-roles)
- [Technology and requirements](#technology-and-requirements)
- [Open the project](#open-the-project)
- [Quick start: JavaFX demo](#quick-start-javafx-demo)
- [Set up PostgreSQL](#set-up-postgresql)
- [Run JavaFX with PostgreSQL](#run-javafx-with-postgresql)
- [Run the console or Swing](#run-the-console-or-swing)
- [Build and test](#build-and-test)
- [End-to-end walkthrough](#end-to-end-walkthrough)
- [Business rules](#business-rules)
- [Architecture and source layout](#architecture-and-source-layout)
- [Database model](#database-model)
- [Configuration reference](#configuration-reference)
- [Troubleshooting](#troubleshooting)
- [Scope and limitations](#scope-and-limitations)
- [Further documentation](#further-documentation)

## Features and roles

The following database-backed JavaFX features require the configured test accounts described below. Ordinary registered accounts use demo services.

| Role | Main capabilities |
| --- | --- |
| Buyer / coffee shop | View an overview; create multi-product demands; browse paginated demands and details; see confirmed supplier allocations; view orders and saved prices; confirm receipt; review a completed order. |
| Supplier | View a weekly supply overview and incoming orders; offer or stop offering existing central products; create, edit and delete eligible weekly supply records. Incoming orders are read-only. |
| Admin | View overview metrics, supplier ranking, weekly supply/demand and supply-composition charts; manage coffee products, coffee shops and suppliers; manage supplier-product offerings; monitor supply, demands, orders and reviews; preview and confirm a weekly allocation batch. |

Buyer submission creates a demand. **Admin allocation confirmation creates orders.** Supplier does not mark orders delivered in the current UI; Buyer owns receipt confirmation.

JavaFX displays the current environment, including `DEMO MODE` or `POSTGRESQL TEST`. In a configured database workspace, connection failures are reported instead of silently displaying demo records. Admin previews of Buyer/Supplier workspaces are read-only and do not log in as the configured Buyer/Supplier.

## Technology and requirements

Versions below come from this repository's build files, rather than a claim that they are the latest releases.

| Component | Requirement / configured version |
| --- | --- |
| JDK | **26**, with both `java` and `javac`; `pom.xml` compiles with release 26. |
| Maven | An installed Maven executable on `PATH`; no Maven wrapper is included. Maven 3.10.0 was available during the README check. |
| JavaFX | Controls and FXML **21.0.6**, resolved by Maven; Maven JavaFX plugin **0.0.8**. |
| Database | PostgreSQL; the schema uses identity and stored generated columns, requiring PostgreSQL 12 or newer at the schema level. See [database validation](database/VALIDATION.md) for recorded verification. |
| PostgreSQL driver | pgJDBC **42.7.14**. |
| Tests | JUnit Jupiter API **5.11.0**, Maven Surefire **3.3.0**. |
| Node.js | **22.20.0 or newer**, only for npm launchers and Neon tooling. Not required for the direct Maven/JavaFX launch. |
| Optional tools | Git, an IDE, pgAdmin or `psql`. |

The desktop interface needs a graphical desktop session. Initial Maven dependency downloads and `npm ci` need network access.

## Open the project

Open the repository root containing `pom.xml`, `src/` and `database/`. On the current development machine:

```powershell
Set-Location 'C:\Users\MY PC\Desktop\java'
```

On another machine, use the location where you cloned or extracted the repository. The ChatGPT project mirror is reference/staging material; use the runnable repository for development.

### IntelliJ IDEA

1. Choose **File → Open** and select the repository directory or `pom.xml`.
2. Import/load it as a Maven project.
3. Set **Project SDK**, the Maven importer JDK and Maven runner JRE to **JDK 26**.
4. Let Maven resolve dependencies.
5. Run the Maven goal `javafx:run` from the Maven tool window, or use the terminal instructions below. For a database launch, add the same environment variables to the Maven run configuration.

### VS Code or another Java IDE

Open the repository root, enable Java/Maven support and select JDK 26. Use the integrated terminal to run the Maven commands in this guide. Prefer the configured JavaFX Maven goal over launching `App.java` as an ordinary Java class without JavaFX runtime settings.

### Check the toolchain

```powershell
java -version
javac -version
mvn -version
```

Both Java commands and the Java version reported by Maven should use JDK 26. If Windows selects an older installation, point this terminal at the installed JDK. For example, on the current machine:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version
```

Replace that directory if your JDK is installed elsewhere. These environment changes apply to the current terminal and processes started from it.

## Quick start: JavaFX demo

To explore the interface without setting up PostgreSQL, open a new terminal with no `COFFEE_TEST_*` account settings and run:

```powershell
mvn javafx:run
```

The configured entry point is `com.coffeemongers.App`. The application opens the sign-in screen.

| Demo account | Email | Password |
| --- | --- | --- |
| Built-in Admin | `admin@coffeemongers.demo` | `CoffeeAdmin2026!` |
| Buyer / Supplier | Register through the application | Choose your own password. |

Public registration supports Buyer and Supplier roles. Passwords require at least 10 characters, including uppercase, lowercase, a number and a symbol. The Admin credential above is a built-in demo credential, not a database password.

Demo accounts and data are held in memory and are lost when the process exits. Registration does not create PostgreSQL coffee-shop or supplier records. Demo demand submission saves a request in memory; it does not perform the real Admin allocation/order workflow.

## Set up PostgreSQL

Skip schema and seed creation if you already have a configured database containing this project's tables. Connect to that database using the next section.

### Create a new local development database

In pgAdmin, create a new empty database, for example `coffee_supplier_allocation_dev`. Open its Query Tool and run the entire files in this order:

1. [database/01_schema.sql](database/01_schema.sql) — creates the 13-table schema.
2. [database/04_current_demo_data.sql](database/04_current_demo_data.sql) — optional rolling demo data for the current and next delivery weeks; requires empty project tables.

Use [database/02_seed_data.sql](database/02_seed_data.sql) instead if you specifically want the fixed October 2026 historical fixture. Choose **one** seed; do not run both on the same database.

Alternatively, with PostgreSQL command-line tools on `PATH`:

```powershell
createdb -h localhost -p 5432 -U postgres coffee_supplier_allocation_dev
psql -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_dev -v ON_ERROR_STOP=1 -f .\database\01_schema.sql
psql -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_dev -v ON_ERROR_STOP=1 -f .\database\04_current_demo_data.sql
```

Use your own PostgreSQL username, host and port. The tools prompt for the database password when required. If they are not on `PATH`, run them from the PostgreSQL installation's `bin` directory or use pgAdmin.

Schema creation is intended to run once on an empty database. The seed refuses to run when project tables already contain data; neither file is a reset/migration command. Use an isolated development/test database for seed and validation scripts.

[database/03_test_queries.sql](database/03_test_queries.sql) creates temporary test data and rolls back its transaction. Run it only against a separate test database. Rolled-back inserts can still advance identity sequences, so do not assume record IDs are always 1.

### Seed dates and current-week testing

The original `02_seed_data.sql` preserves its **ISO weeks 41–43 of 2026** historical examples. The separate `04_current_demo_data.sql` calculates the current and following ISO week/year from the database clock in **Asia/Ho_Chi_Minh**, including year boundaries. Both fixtures use kilograms and VND.

For a ready-to-run demonstration on a new database, use the rolling fixture. It creates current-week demands with split supply, competition, shortage and an inactive offering, plus a separate next-week demand. It does not precreate allocations, orders or reviews. The console starts in the current Vietnam business week; use **menu 13** to select another ISO year/week. JavaFX and Swing keep their existing date/week selectors. See [the rolling demo guide](docs/current-demo.md) for exact expected results.

The seed does not move stored records as time passes. If its current week later expires, use a new empty demo database and rerun it, or create new supply and demands for a current/future week through the app. Historical data and the expiry rules remain intact.

### Find the IDs for configured accounts

Use pgAdmin Query Tool on your selected database:

```sql
SELECT shop_id, name, is_active FROM coffee_shops ORDER BY shop_id;
SELECT supplier_id, name, is_active FROM suppliers ORDER BY supplier_id;
SELECT product_id, name, unit, is_active FROM coffee_products ORDER BY product_id;
```

Choose an active shop and supplier. If you did not seed the database, the configured test Admin can add central products, shops and suppliers through its catalog screens. A business record and a login account are separate concepts.

## Run JavaFX with PostgreSQL

### 1. Set the JDBC connection

In the same PowerShell terminal used to launch Maven, replace the example values with your database settings:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/coffee_supplier_allocation_dev'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = '<your database password>'
```

For an existing Neon database, use its host/database/credentials in the JDBC settings. For example:

```powershell
$env:DB_URL = 'jdbc:postgresql://<your-neon-host>/<database>?sslmode=require&channelBinding=require'
$env:DB_USER = '<your Neon database role>'
$env:DB_PASSWORD = '<your Neon database password>'
```

Replace every `<...>` value. Keep real credentials out of tracked files. The Java connection class reads `DB_URL`, `DB_USER` and `DB_PASSWORD` directly; it does **not** automatically read `.env.local` or convert `DATABASE_URL` when launched by Maven.

### 2. Configure the test identities

Replace the sample IDs with the active records selected above. Replace the sample password text with passwords of your choice that meet the app's password rules:

```powershell
$env:COFFEE_TEST_SHOP_ID = '<active shop_id>'
$env:COFFEE_TEST_BUYER_PASSWORD = '<your Buyer test password>'
$env:COFFEE_TEST_SUPPLIER_ID = '<active supplier_id>'
$env:COFFEE_TEST_SUPPLIER_PASSWORD = '<your Supplier test password>'
$env:COFFEE_TEST_ADMIN_PASSWORD = '<your Admin test password>'
```

You can enable only the roles you need. Buyer requires its shop ID and password; Supplier requires its supplier ID and password; Admin requires its password. Configured passwords require at least 10 characters, uppercase, lowercase, a digit and a symbol. Invalid ID/password configuration can prevent startup.

### 3. Start the app and sign in

```powershell
mvn javafx:run
```

| PostgreSQL test login | Password | Database scope |
| --- | --- | --- |
| `buyer@coffeemongers.test` | `COFFEE_TEST_BUYER_PASSWORD` value | The shop mapped by `COFFEE_TEST_SHOP_ID`. |
| `supplier@coffeemongers.test` | `COFFEE_TEST_SUPPLIER_PASSWORD` value | The supplier mapped by `COFFEE_TEST_SUPPLIER_ID`. |
| `admin@coffeemongers.test` | `COFFEE_TEST_ADMIN_PASSWORD` value | Integrated Admin features across the selected database. |

Check that the workspace displays `POSTGRESQL TEST`. Signing in to `admin@coffeemongers.demo` still uses demo services even when database variables exist.

Use **Sign out** to switch between the configured roles. IDs and passwords are read at startup: changing their terminal values requires restarting the application. Creating a new shop/supplier in Admin does not create another login; change the corresponding mapped ID and restart to use that record with the test login.

## Run the console or Swing

These are separate interfaces. `npm start` opens the console, and `npm run desktop` opens Swing; neither command starts JavaFX.

### Optional npm setup

```powershell
node --version
npm.cmd ci
npm.cmd run build
```

On Windows, `npm.cmd` also works when PowerShell blocks the `npm.ps1` shim. The npm build runs Maven packaging/tests and copies runtime dependencies to `target/dependency`.

The npm launchers accept either the existing JDBC variables above or a PostgreSQL `DATABASE_URL`. They can read database settings from `.env`, otherwise `.env.local`. Copy [.env.example](.env.example) only when you need a new local example file; do not overwrite an already configured `.env.local`. Fill in real values locally.

Database selection order in the launcher is:

1. Process `DATABASE_URL`.
2. Process `DB_URL` together with `DB_USER` and `DB_PASSWORD`.
3. Database settings from the selected `.env` or `.env.local` file.

The launcher converts a Postgres URL to JDBC settings, decodes credentials, maps `channel_binding` to `channelBinding`, and requires TLS/channel binding for its URL-adapter path. For a local database without those settings, use the JDBC variables. Maven/Java launches do not use this npm environment loader.

### Console

After building and configuring a database:

```powershell
npm.cmd start
```

Or, without Node, use the JDBC variables and run:

```powershell
mvn package dependency:copy-dependencies '-DincludeScope=runtime'
java -cp 'target/classes;target/dependency/*' com.coffeeallocation.Main
```

The classpath example uses Windows' `;` separator; use `:` on other systems.

| Menu | Action |
| --- | --- |
| 1–4 | View active products, weekly supply, open demand items and active shops. |
| 5 | Create an OPEN demand. |
| 6 | Match an OPEN demand item to eligible suppliers. |
| 7 | Preview shared allocation; does not reserve supply. |
| 8 | Preview and confirm allocation, deduct supply and create orders. |
| 9 | View orders and details. |
| 10 | Complete a PENDING order. |
| 11 | Submit/view supplier reviews. |
| 12 | View supplier review history at a cutoff. |
| 13 | Change the delivery ISO year/week for supply, demand, matching and allocation operations. |
| 0 | Exit. |

**Console delivery period:** startup selects the current week using `Asia/Ho_Chi_Minh`. Menu **13** accepts a valid ISO week-based year and week; blank input cancels without changing the selection. Historical weeks can be selected for viewing, while backend rules continue to reject new demands, matching and allocation for expired weeks. Orders and review/history operations keep their existing behavior. Selection lasts until changed or the console exits; if a session crosses Monday midnight, use menu 13 to select the new week.

### Swing

With the npm environment loader:

```powershell
npm.cmd run desktop
```

Or, after `mvn compile` has downloaded pgJDBC and the three JDBC environment variables are set:

```powershell
.\run-desktop.ps1
```

To choose the current machine's JDK explicitly:

```powershell
.\run-desktop.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.2.1'
```

The PowerShell launcher compiles the backend/Swing sources into `target/desktop-classes` and launches `com.coffeeallocation.DesktopMain`. Swing provides demand creation, weekly allocation/confirmation, orders, reviews and supplier history. It uses manually supplied shop IDs rather than JavaFX test-account sign-in. It does not provide all JavaFX Admin/Supplier screens.

## Build and test

Run from the repository root with JDK 26 selected:

```powershell
mvn compile
mvn test
mvn package
```

`package` includes tests and produces `target/coffee-supplier-allocation-1.0-SNAPSHOT.jar`. The JAR is not configured as a self-contained executable with bundled JavaFX/database libraries; use the documented launch commands rather than `java -jar`.

The regular suite includes validation, authentication/demo workflow, ranking, expiry rules, charts and JavaFX FXML/navigation tests. The JavaFX tests require a desktop display. PostgreSQL integration fixtures are opt-in and normally skip.

### PostgreSQL integration fixtures

Each fixture creates schema and synthetic data in a **new, empty, disposable database** with the exact name it checks. Do not point it at the app database or a shared production database.

| Test class | Required database | Opt-in environment variable |
| --- | --- | --- |
| `BuyerWorkspaceIntegrationTest` | `coffee_buyer_workspace_test` | `COFFEE_BUYER_WORKSPACE_INTEGRATION=true` |
| `SupplierWorkspaceIntegrationTest` | `coffee_supplier_workspace_test` | `COFFEE_SUPPLIER_WORKSPACE_INTEGRATION=true` |
| `SupplierRankingIntegrationTest` | `coffee_ranking_test` | `COFFEE_RANKING_INTEGRATION=true` |
| `DemandExpiryIntegrationTest` | `coffee_demand_expiry_test` | `COFFEE_DEMAND_EXPIRY_INTEGRATION=true` |
| `WeeklyMarketIntegrationTest` | `coffee_weekly_chart_test` | `COFFEE_CHART_INTEGRATION=true` |
| `SupplyCompositionIntegrationTest` | `coffee_composition_test` | `COFFEE_COMPOSITION_INTEGRATION=true` |
| `CurrentDemoIntegrationTest` | `coffee_current_demo_test` | `COFFEE_CURRENT_DEMO_INTEGRATION=true` |

Example, in a separate test terminal after creating an empty `coffee_ranking_test` database:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/coffee_ranking_test'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = '<test database password>'
$env:COFFEE_RANKING_INTEGRATION = 'true'
mvn '-Dtest=SupplierRankingIntegrationTest' test
```

Use a fresh empty database for each fixture invocation and enable only the matching fixture. A successful regular test run with integration tests skipped does not establish live database or concurrency correctness.

### npm helper checks

```powershell
npm.cmd test
```

This tests the Node database environment adapter; it is separate from `mvn test`.

`npm.cmd run test:neon` is an optional configured-Neon smoke check. It checks connection requirements, the 13 tables, reads and a temporary write followed by rollback. It accesses the configured database and is not part of the offline/unit test suite. See [NEON.md](NEON.md) and [NEON_VERIFICATION.md](NEON_VERIFICATION.md).

## End-to-end walkthrough

Use a development database and enable all three PostgreSQL test identities before launch.

1. **Admin:** sign in as `admin@coffeemongers.test`. Verify an active shop, supplier and central coffee product. Note the selected product's unit.
2. **Supplier:** sign out and sign in as `supplier@coffeemongers.test`. In **My Products**, offer that product. In **Weekly Supply**, declare a positive quantity and unit price for the current or a future delivery week.
3. **Buyer:** sign out and sign in as `buyer@coffeemongers.test`. In **Create Demand**, choose the same product and a delivery date in the same ISO week. Enter a quantity within available supply, review and submit.
4. **Buyer:** open **My Demands → Demand Details** and verify the saved demand ID, quantities, units, delivery week and OPEN status. No order should exist just from submission.
5. **Admin:** sign in with the test Admin, open allocation preview and load the same week. Check the demands, suppliers, unit prices and proposed quantities. Confirmation applies to **all fully supplied lines in the weekly preview**, including other shops' eligible demands.
6. **Admin:** confirm the displayed batch. If data changed, refresh the preview and inspect it before confirming again. Record the returned order IDs.
7. **Supplier:** inspect **Incoming Orders** and the weekly declared/allocated/remaining quantities. The Supplier order screen is read-only.
8. **Buyer:** refresh **My Orders**, inspect saved line prices, then **Confirm received** to complete the order. Submit the three Yes/No review criteria and an optional comment.
9. **Admin:** refresh **Orders & Activity**, **Supplier Reviews** and Overview to inspect persisted results. Top Suppliers requires at least five eligible reviews, so a supplier's first review does not automatically place it in the Top 3.

For a shortage example, request more than the remaining eligible supply: that product line receives no partial allocation. Other fully supplied lines can still be confirmed; the entire demand becomes ALLOCATED only once every line is fulfilled.

## Business rules

### Weekly quantities and allocation

- Supply belongs to a supplier-product pair and an **ISO week/year**, starting Monday. Supply from another week is not borrowed or carried forward.
- Weekly supply is a commitment for allocation, not physical warehouse inventory. `remaining_quantity` is already net of committed allocation; do not subtract allocation twice.
- Eligibility requires an active shop/product/supplier/offering as appropriate, matching product/week, and positive available supply.
- Demand priority is `request_date`, then `demand_id`, then `demand_item_id`, ascending.
- Eligible supplier supply is ordered by lowest `unit_price`, then lowest `supplier_id`.
- Allocation is **all-or-nothing per demand item**. A line may split across several suppliers when together they can cover it fully. An unsuccessful line consumes no supply, allowing later eligible lines to use it.
- Preview uses a shared, decreasing supply balance across demand lines, without persisting reservations. The greedy policy is deterministic; it is not a claim of globally optimal allocation.
- Confirmation uses a `SERIALIZABLE` transaction, locks relevant demand/supply rows, recomputes the preview and compares it to the displayed snapshot. Stale data or serialization conflicts require a fresh preview.
- Runs, allocations, supply deductions, orders and demand status updates commit together or roll back. The current implementation creates an order for each supplier allocation rather than grouping every line into a single order.
- Quantity and money use `BigDecimal` / PostgreSQL `NUMERIC`. Product units are preserved; the app does not convert kg/g or add incompatible units. The demo convention is VND and kg.
- Supply edits cannot lower declared quantity below commitments. Product/week stay fixed when editing a saved row. Deletion requires no committed quantity and no allocation history. Saved order/allocation prices do not change when weekly prices change.

### Lifecycle and expiry

| Record | Current workflow |
| --- | --- |
| Demand | `OPEN` → `ALLOCATED` when every product line is fulfilled; overdue unfulfilled OPEN demands become `EXPIRED`. `CANCELLED` is a separate existing state, not a new cancellation action. |
| Allocation / confirmed run | Written as `CONFIRMED` by confirmation. |
| Order | Created `PENDING`, then Buyer receipt changes it to `COMPLETED`. |
| Review | One submission per completed order through the service, for that order's Buyer and Supplier. |

The delivery week closes at midnight after Sunday in **Asia/Ho_Chi_Minh**. Expiry does not cancel existing allocations/orders, refund supply or prevent receipt/review of an existing eligible order. New demands and allocations for past delivery weeks are rejected.

JavaFX checks expiry every 60 seconds in a configured connected workspace. Relevant foreground reads also catch up expiry. There is no standalone background server while all apps are closed; persisted statuses catch up on the next relevant access.

### Reviews and dashboard ranking

Reviews contain three Boolean criteria: quality, quantity and on-time feedback, plus an optional comment. They are not star ratings or independently measured delivery punctuality.

Eligible history uses an order's first valid review, ordered by review timestamp then ID, while the order is currently COMPLETED and the review falls between order creation and the report cutoff. Later duplicate legacy reviews remain stored but do not multiply the aggregate.

```text
criterion rate = passing reviewed orders / contributing reviewed orders × 100
dashboard score = 0.40 × quality rate
                + 0.30 × quantity rate
                + 0.30 × on-time rate
```

Top Suppliers includes at most three active suppliers with at least five valid reviews, ordered by score descending, review count descending and supplier ID ascending. One to four reviews is limited data; no reviews means no invented score.

**The dashboard review score does not replace price-priority allocation.** The baseline contains `supplier_scores`, but current allocation confirmation does not populate it with the dashboard ranking formula. See [supplier-ranking.md](docs/supplier-ranking.md).

## Architecture and source layout

```text
JavaFX / Swing / console views and controllers
                 |
        services and use cases
        /                  \
allocation algorithms      JDBC DAOs
                                |
                           PostgreSQL
```

Services own validation and transaction boundaries. DAOs use prepared SQL and map rows to models. JavaFX/Swing database work runs off the UI thread; display updates return to the appropriate UI thread.

```text
pom.xml                         Maven dependencies, compiler, JavaFX entry point
package.json                    Optional npm launch/build/check commands
run-desktop.ps1                 Windows Swing launcher
database/                       Schema, seed and SQL validation
docs/                           Feature decisions and verification records
scripts/                        npm/JDBC environment adapter and smoke check
src/main/java/com/coffeeallocation/
  Main.java                     Console entry point
  DesktopMain.java              Swing entry point
  algorithm/                    Price priority and shared allocation
  config/                       JDBC connection configuration
  console/                      Console interaction
  dao/                          SQL and persistence
  model/                        Backend domain/read models
  service/                      Business use cases and transactions
  view/                         Swing views and UI helpers
src/main/java/com/coffeemongers/
  App.java                      JavaFX entry point
  controller/                   JavaFX controllers
  model/                        UI, authentication and schema models
  service/                      UI adapters, sessions, demo/live composition
  view/                         JavaFX navigation and presentation helpers
src/main/resources/             FXML, CSS and images
src/test/java/                  JUnit and opt-in PostgreSQL tests
target/                         Generated classes, test reports and JARs
```

`AppServices` composes demo and configured database services. Package names alone do not establish whether a feature is live: `com.coffeemongers.service` includes real database adapters as well as in-memory implementations.

## Database model

The baseline has **13 application tables**:

| Table | Purpose |
| --- | --- |
| `coffee_shops` | Buyer business records and contact details. |
| `suppliers` | Supplier business records and contact details. |
| `coffee_products` | Central product catalog, units and active status. |
| `supplier_products` | Unique supplier-product offerings. |
| `supplier_weekly_supply` | Declared/remaining quantity and unit price per offering/week. |
| `demands` | Shop request header and required ISO week/year. |
| `demand_items` | Product and requested quantity for each demand line. |
| `recommendation_runs` | Allocation/recommendation execution linked to a demand item. |
| `supplier_scores` | Baseline storage for per-run supplier score components. |
| `allocations` | Confirmed quantities, unit prices, run and source supply. |
| `orders` | Buyer/Supplier transaction linked to an allocation. |
| `order_items` | Saved product/quantity/price; generated rounded line total. |
| `supplier_reviews` | Boolean Buyer feedback linked to an order and Supplier. |

The schema defines identity primary keys, foreign keys, unique/check constraints and indexes. Cross-row allocation consistency also depends on the service transaction protocol. Active/inactive catalog status preserves historical demands, supply, orders and reviews instead of deleting them.

See [database/01_schema.sql](database/01_schema.sql) for the authoritative DDL and [db_schema.png](db_schema.png) for the reference diagram. The SQL includes documented additions to that diagram, including the required week/year and allocation run/supply links.

Timestamps are PostgreSQL `TIMESTAMP` without time zone. Historical cutoff reports expose the database session timezone; writers/reporters need a consistent convention. Demand expiry separately converts the database wall clock to the named business timezone. Do not assume changing the operating system timezone rewrites stored history.

## Configuration reference

| Variable / file | Used for |
| --- | --- |
| `JAVA_HOME` | JDK selection for Maven and the Swing launcher. |
| `DB_URL` | Java JDBC URL, including the `jdbc:postgresql://` prefix. |
| `DB_USER`, `DB_PASSWORD` | Database role and password read by Java. |
| `DATABASE_URL` | Postgres URI consumed by the optional npm environment adapter. |
| `DATABASE_URL_UNPOOLED` | Direct Neon URI for administration/migrations; not automatically selected as the application's database by the npm adapter. |
| `NEON_BRANCH` | Neon environment metadata; does not itself choose the JDBC connection. |
| `COFFEE_TEST_SHOP_ID` | Active shop mapped to the configured Buyer account. |
| `COFFEE_TEST_BUYER_PASSWORD` | Enables the mapped Buyer login together with its shop ID. |
| `COFFEE_TEST_SUPPLIER_ID` | Active supplier mapped to the configured Supplier account. |
| `COFFEE_TEST_SUPPLIER_PASSWORD` | Enables the mapped Supplier login together with its supplier ID. |
| `COFFEE_TEST_ADMIN_PASSWORD` | Enables the PostgreSQL test Admin login. |
| `.env` / `.env.local` | Optional local database settings loaded by npm launchers, not by Java/Maven automatically. |
| `.env.example` | Tracked placeholder example. |
| `.neon`, `neon.ts` | Local Neon context and repository Neon configuration; see [NEON.md](NEON.md). |

`.env*` files (except `.env.example`), `.neon`, `node_modules/` and `target/` are listed in `.gitignore`. Do not include real connection strings or local passwords in commits or screenshots. For existing Neon infrastructure, use the selected database; README setup does not require deploying/reprovisioning it. Use a direct connection for schema administration and isolated development data for experiments; see the [official Neon connection-pooling documentation](https://neon.com/docs/connect/connection-pooling).

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| `release version 26 not supported`, class-version error, or Maven uses Java 8 | Set `JAVA_HOME` and put its `bin` first on `PATH`; confirm `java`, `javac` and Maven all use 26. Check the IDE's Maven runner too. |
| `mvn` is not recognized | Install/configure Maven on `PATH`; open a new terminal and confirm `mvn -version`. |
| PowerShell blocks `npm.ps1` | Use `npm.cmd` as in this guide. |
| PowerShell blocks `run-desktop.ps1` | Use `npm.cmd run desktop`, whose Windows launcher supplies a process-level execution-policy option. Organizational policies can still restrict execution. |
| `Missing environment variable: DB_URL` | Set all three JDBC variables in the same process that starts Java/Maven. A `.env.local` file alone does not configure `mvn javafx:run`. |
| `relation ... does not exist` | Verify host/database/schema and apply `01_schema.sql` once to the intended empty development database. |
| Table already exists / seed refuses to run | These scripts are for initial setup. Use the existing database or create another empty development database. |
| Query Tool remains in an aborted transaction | Run `ROLLBACK;` before the next command, then address the original error. |
| Test login missing or startup rejects configuration | Check its enabling variables, positive ID and password complexity. Restart after changes. |
| Workspace still says Demo | Use the `@coffeemongers.test` login for the configured role; ordinary registration and the built-in demo Admin remain in memory. |
| No eligible supplier or empty preview | Verify the same product and ISO year/week, active shop/supplier/product/offering, remaining supply, unallocated OPEN demand lines and a current/future delivery week. |
| Delivery week expired | Select a current/future week in JavaFX/Swing or console menu 13. The original October 2026 seed is historical; use the rolling fixture on a fresh demo database for a current demonstration. |
| Preview changed / serialization conflict | Reload and inspect the latest preview; confirm again only after checking orders and current quantities. |
| Connection failed immediately after a write | Refresh orders/demands/supply to establish the saved result before resubmitting. |
| Supplier cannot lower/delete supply | Declared quantity must cover commitments; rows with committed allocation or allocation history cannot be removed. |
| Review rejected | Use the owning Buyer and a COMPLETED order with no previous review. |
| Supplier absent from Top 3 | It needs active status, at least five eligible reviews and a qualifying rank. |
| FXML tests fail on a server/headless terminal | They start JavaFX and require a graphical session. Inspect `target/surefire-reports` for the actual failure. |
| Double-clicking the packaged JAR does not launch the app | Use `mvn javafx:run` or the documented classpath/launcher commands. |

### README verification on 2026-11-21

The current source was copied to a separate validation directory without database credentials. With JDK 26.0.2.1 and Maven 3.10.0, Maven packaging and runtime dependency copying succeeded: 64 tests were discovered, 58 passed, six opt-in PostgreSQL tests skipped, and none failed. The JavaFX FXML/navigation test passed. All 11 Node environment-adapter tests also passed.

Existing JavaFX native-access/deprecation and chart CSS warnings appeared during tests. No live database integration, Neon smoke check or full manual role workflow was rerun for this documentation update; the walkthrough above is the procedure to follow on your development database.

On **2026-11-22**, the selectable console-week change passed 65 regular tests,
including seven new selection/ISO-week tests. The rolling fixture and actual
console database actions passed the separate opt-in `CurrentDemoIntegrationTest`
on disposable local PostgreSQL 18 data. This tested current/future-week isolation,
shifted IDs, ISO rollover, repeat-seed refusal and supply reconciliation; see
[current-demo.md](docs/current-demo.md). No application database was modified.

## Scope and limitations

- This is a desktop MVP with configured database test identities and session-only demo registration, not a production multi-user authentication system.
- There is no separate REST API, browser/mobile frontend, payment processing, delivery routing, distance optimization or physical inventory ledger.
- JavaFX, Swing and the console expose different subsets of functionality. JavaFX is the broadest role-based interface; the console supports selecting the delivery ISO year/week through menu 13.
- Catalog deactivation and offering-stop actions retain historical data. A general cancellation/refund workflow is not added merely because a CANCELLED state exists.
- Allocation follows price/demand priority; dashboard reputation ranking is a separate report. Full persisted scoring snapshots/configuration are not claimed by the presence of the `supplier_scores` table.
- There is no always-running maintenance server or automatic cross-session screen synchronization. Use Refresh/reopen views to observe other users' changes.
- Existing feature documents include historical verification reports and earlier implementation notes. Check their dates and the current source; those results do not mean the current README work reran every live database/UI scenario.

## Further documentation

- [Project decisions](docs/decisions.md)
- [Database setup and conventions](database/README.md) and [recorded validation](database/VALIDATION.md)
- [Buyer workspace](docs/buyer-workspace.md)
- [Supplier workspace](docs/supplier-workspace.md) and [supplier product integration](docs/supplier-products-integration.md)
- [Admin overview](docs/admin-overview.md), [coffee products](docs/admin-coffee-products.md) and [business catalogs](docs/admin-business-catalogs.md)
- [Admin supplier offerings](docs/admin-supplier-products.md), [supply monitoring](docs/admin-supply-monitoring.md), [orders/activity](docs/admin-orders-activity.md) and [reviews](docs/admin-supplier-reviews.md)
- [Review ranking](docs/supplier-ranking.md)
- [Weekly supply/demand chart](docs/weekly-supply-demand-chart.md) and [supply composition](docs/supply-composition-chart.md)
- [Demand expiry](docs/demand-expiry.md)
- [Current/future-week demo and verification](docs/current-demo.md)
- [UI feedback](docs/ui-ux-polish.md) and [window/navigation behavior](docs/window-navigation.md)
- [Swing interface](docs/phase8-desktop.md) and [historical verification](docs/phase8-verification.md)
- [Neon setup](NEON.md) and [Neon verification](NEON_VERIFICATION.md)
- [Development guidance](AGENTS.md)

This README was updated on **2026-11-22** for selectable console weeks and the rolling demo fixture. Older database/phase documents sometimes describe earlier milestones; use the actual source and the rules above for current launch and workflow behavior.
