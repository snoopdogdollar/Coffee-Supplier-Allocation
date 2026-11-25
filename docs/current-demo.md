# Current and future delivery-week demonstration

Added 2026-11-22. The console no longer hard-codes October 2026. The historical
seed remains available unchanged; the new seed is an alternative on an empty
development/test database.

## Console selection

Start the console using the [root README](../README.md). Its initial delivery week
is the current ISO week in `Asia/Ho_Chi_Minh`. Choose **13**, enter the ISO
week-based year and week, then use the existing actions. The selected period is
shown on every main menu and applies to options 2, 3, 5, 6, 7 and 8. Blank input
at either prompt or end of input keeps the previous selection. Invalid text,
out-of-range years and impossible week-53 values are rejected without exiting.

Historical weeks may be selected for supply/demand viewing. The existing backend
still refuses new demand creation, matching, allocation preview and confirmation
for expired delivery weeks. Those rejections return to the menu. Orders, receipt,
reviews and supplier history keep their existing scopes and behavior. Changing
the period does not change stored records. A long-running session keeps its
selection across Monday midnight; choose menu 13 to switch to the new week.

## Load a fresh rolling fixture

Create a **new, empty** database such as `coffee_supplier_allocation_demo`. In
pgAdmin, select that database, open Query Tool, run all of `01_schema.sql`, then
all of `04_current_demo_data.sql`. Do not run the historical seed there as well.

With PostgreSQL tools on PATH, the equivalent PowerShell commands are:

```powershell
createdb -h localhost -p 5432 -U postgres coffee_supplier_allocation_demo
psql -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_demo -v ON_ERROR_STOP=1 -f .\database\01_schema.sql
psql -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_demo -v ON_ERROR_STOP=1 -f .\database\04_current_demo_data.sql
```

Change the connection details for your development installation. The seed checks
all 13 application tables under locks and refuses the entire operation if any
already has rows. It does not reset, delete, migrate or append to application data.
After an error in pgAdmin, issue `ROLLBACK;` before the next command. Identity
sequences need not start at 1: the fixture uses `RETURNING`, and its final queries
show actual shop/supplier/product IDs for the configured JavaFX test accounts.

The current and next delivery weeks come from the database clock in the business
timezone. ISO week-based years are computed separately for each actual date, so
week 53 and December/January transitions do not require editing the script.
Data stays in the weeks saved when the script ran. Use a fresh database for a
later demonstration, or add new current/future-week records through the app.

## What the fixture contains

Two active shops, three active suppliers, three products (all `kg`), six offerings
and twelve weekly supply rows. One Decaf offering is stopped. There are four
current-week OPEN demands with five lines, and one next-week OPEN demand with
two decimal-quantity lines. Runs, scores, allocations, orders and reviews start
empty so the app demonstrates real confirmation rather than precreated orders.

Current-week eligible supply:

| Product | Eligible suppliers and declared quantities | Total |
| --- | --- | --- |
| Arabica | New Harvest 20 kg at 118000; Highland Beans 30 kg at 120000; Valley Coffee 40 kg at 125000 VND/kg | 90 kg |
| Robusta | Highland Beans 100 kg at 80000; Valley Coffee 80 kg at 82000 VND/kg | 180 kg |
| Decaf | Saved quantity exists, but the offering is stopped | 0 kg eligible |

Before adding any other records, current-week shared preview should produce:

| Demand | Requested product quantities | Expected result |
| --- | --- | --- |
| First Riverside request | Arabica 50 kg; Robusta 10 kg | Both lines fully supplied; Arabica splits across two suppliers. |
| Morning Cup Arabica request | Arabica 60 kg | Unallocated: only 40 kg remains after Riverside. It consumes nothing. |
| Morning Cup Robusta request | Robusta 120 kg | Fully supplied across two suppliers. |
| Riverside Decaf request | Decaf 10 kg | Unallocated: no active offering. |

Current-week confirmation creates **five PENDING orders**, confirms three product
lines and marks two demands ALLOCATED. Arabica remaining is 40 kg, Robusta
remaining is 50 kg, and the stopped Decaf supply is unchanged. Next-week supply
is not consumed by this operation.

Choose menu 13 and select the next week printed by the seed. That week has its
own supply and a request for **Arabica 10.25 kg** and **Robusta 5.50 kg**.
Confirming that preview creates two more orders. Use the Buyer receipt/review
workflow to create real completed orders and reviews afterward. Dashboard Top
Suppliers still requires five eligible reviews; no reputation scores are invented.

## Automated verification

Regular `mvn test` includes `DeliveryWeekTest` and `DeliveryWeekConsoleTest` for
ISO year boundaries, week 53, Vietnam timezone rollover, invalid input, cancel/EOF
and the main menu selection/exit path.

The opt-in `CurrentDemoIntegrationTest` requires a **new, empty** PostgreSQL
database named exactly `coffee_current_demo_test`. It applies the schema itself;
do not preload schema or seed. In a separate test terminal:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/coffee_current_demo_test'
$env:DB_USER = '<test database role>'
$env:DB_PASSWORD = '<test database password>'
$env:COFFEE_CURRENT_DEMO_INTEGRATION = 'true'
mvn '-Dtest=CurrentDemoIntegrationTest' test
```

The test checks shifted identity values, four simulated ISO year-boundary seed
dates (rolled back), the real-clock fixture, refusal of a second seed with all
13 table fingerprints preserved, current-week allocation and future-week supply
isolation. It then drives the actual console through next-week supply, demands,
matching, preview and confirmation; creates a demand in a newly selected week;
and verifies a past-week rejection returns to the menu. All real-clock fixture
rows are synthetic data in that disposable test database.

Verified on 2026-11-22 using JDK 26.0.2.1, Maven 3.10.0 and a separate local
PostgreSQL 18 instance: all 65 regular tests passed (six pre-existing opt-in
database tests skipped in that run), including the seven new console/week tests
and the existing JavaFX FXML/navigation test. The new PostgreSQL integration test
passed separately with its opt-in enabled. Its four simulated dates covered ISO
week 53, December/January transitions and the following week's year. All actual
database writes were confined to `coffee_current_demo_test`; no application
database was accessed. The existing JavaFX chart CSS warnings were still emitted.
