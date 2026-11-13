# PostgreSQL Admin Orders & Activity

Use the existing DB_URL, DB_USER, DB_PASSWORD and COFFEE_TEST_ADMIN_PASSWORD settings.
Sign in as admin@coffeemongers.test and open Orders & Activity. The Market Activity
tab in the existing management screen also links here. Demo Admin navigation keeps
its existing demo behavior. The configured Admin Overview and other integrated
Admin monitoring screens also read PostgreSQL.

Orders lists order/demand IDs, date, shop and supplier IDs/names, status, line count
and total. Demands lists IDs, request date, required ISO year/week, shop, status
and line count. Inactive parties and products do not hide historical records.
View Details reads product quantities and units; order details include saved unit
prices and line totals. Demand notes and per-line notes are also shown. Quantities
with different units are not added into a misleading shared total.

Search is a literal, case-insensitive substring across IDs, party names, status,
dates and product IDs/names; demand notes and required ISO week are also searchable.
Press Search or Enter to apply it to both tabs. Refresh reloads the first page of
both tabs with the text currently in the search field. Search is bound with JDBC
parameters, including quotes and percent/underscore characters.

Each tab starts with up to 20 headers and its own descending-ID cursor. Load More
appends the next page for the selected tab using the last applied search. A refresh
is needed to include newly created records above the current cursor. Details are
loaded separately when requested. Database reads run outside the JavaFX thread;
combined header refresh and demand details use read-only repeatable-read snapshots.
Order details reuse the existing read-only OrderService.

This screen provides monitoring and does not mutate orders, demands, quantities,
reviews or prices. Receipt and review remain in the existing Buyer workflow.
No schema changes or new dependencies are required. Tests run against a disposable
local PostgreSQL database, separate from coffee_supplier_allocation.

Verified on 2026-11-12: 21 Maven tests, 33 PostgreSQL checks, 15 JavaFX workflow
checks and 5 checks in a separate JavaFX process passed. Fixtures exercised multiple
pages, repeated demand lines, mixed units, literal search characters, inactive
parties/products, Buyer/Supplier comparisons, receipt/review updates and new orders
after a refresh. Changing weekly supply prices left existing order prices intact.
The fixed details/paging actions were checked in a 720px scene.
