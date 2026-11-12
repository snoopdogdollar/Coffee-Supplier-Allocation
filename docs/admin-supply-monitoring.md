# PostgreSQL Admin Supply Monitoring

Start Desktop/java using DB_URL, DB_USER, DB_PASSWORD and the existing
COFFEE_TEST_ADMIN_PASSWORD setting. Sign in as admin@coffeemongers.test and open
Supply Monitoring. Its existing management tab also links to this view. Demo Admin
keeps the demo view; configured Admin access requires its Admin workspace.

Choose an ISO week-numbering year and week, optionally enter supplier/product name
or ID substrings, then press Load Week, Refresh or Enter. The current ISO week is
loaded on entry. Both filters are applied together, case-insensitively and literally:
quotes, percent and underscore are data rather than SQL or wildcard syntax.
Invalid ISO years/weeks and overlong filters are rejected before querying.

Select All weeks (all years) to include supply across every ISO year/week. This
disables the year/week inputs and reloads from the first page; supplier and product
filters still apply. Clear the checkbox to return to the year/week in the form.
The ISO week column identifies each row's actual week. Blank supplier/product fields
only remove those filters; they do not change the selected week scope. All-weeks
results are also paginated: Load More appends the next 20 matches. The status tells
you whether more matches remain or all matching records have been loaded.

The all-weeks addition was verified on 2026-11-13 with 24 Maven tests and 19
PostgreSQL/JavaFX checks. The fixture included 30 rows spanning four ISO periods
across two years; both all-weeks and single-week pagination, filtering, transitions
and unchanged source data were checked.

The table lists supply ID, supplier/product IDs and names, unit, initial quantity,
committed quantity, remaining quantity, current supply price and eligibility.
Committed = initial - remaining, using the same accounting as Supplier Weekly Supply.
Receipt and review do not consume supply again. Different products or units are not
combined into one quantity total. Prices here are current weekly supply prices;
Orders & Activity continues to show the prices saved on each order.

Inactive suppliers, inactive products, stopped offerings and rows with no remaining
quantity stay visible for monitoring. Eligibility explains those conditions.
Available means an active supplier, product and offering with positive remaining
quantity for that row's ISO week; it does not guarantee a demand can be fulfilled.
The allocation preview also considers demand requirements and shared capacity.

Pages contain up to 20 rows, ordered by descending supply ID. Load More uses the
last successfully applied filters. The Showing label identifies the loaded week
and filters even if the form is edited. Refresh reloads from the first page using
the current form values. Source data comes from read-only JDBC snapshots off the
JavaFX thread. Monitoring does not edit supply, allocations, orders or reviews.

No schema changes or extra dependencies are required. Verification runs against a
disposable local PostgreSQL database, separate from coffee_supplier_allocation.

Verified on 2026-11-13: 24 Maven tests, 30 PostgreSQL checks, 21 JavaFX workflow
checks and 6 checks in a separate JavaFX process passed. Fixtures covered pagination,
literal filters, mixed units, inactive supplier/product/offering states, zero quantity
and price, ISO week 53 and year boundaries, Supplier edits and new supply, allocation
confirmation, receipt and review. Admin quantities matched Supplier Weekly Supply and
allocation inputs. Supply price edits preserved saved order prices. The fixed paging
action was checked in a 720px scene.
