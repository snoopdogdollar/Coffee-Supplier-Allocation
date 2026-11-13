# PostgreSQL Admin Overview

Use DB_URL, DB_USER, DB_PASSWORD and the existing COFFEE_TEST_ADMIN_PASSWORD setting.
Sign in as admin@coffeemongers.test. In the Admin workspace, Overview now reads a
single read-only repeatable-read snapshot from PostgreSQL, off the JavaFX thread.

- Total Orders counts all saved orders across all dates and statuses.
- Pending Orders counts exactly status PENDING.
- Active Suppliers counts supplier records with is_active = true, independent of
  whether they have products or weekly supply.
- Recent Orders contains at most 10 orders by descending order ID, including date,
  actual shop/supplier IDs and names, item count, saved order total and current status.
  Totals are from order_items; quantities with different units are not summed.

Historical orders remain counted and visible when their supplier or shop is inactive.
The All time label replaces the old illustrative 30-day label. The static trend and
seeded supplier ranking are hidden in this PostgreSQL workspace. Demo Admin and demo
workspace previews retain their explicitly labeled demo presentation. The environment
badge identifies the configured PostgreSQL Admin workspace; the load status reports
whether the database read succeeded.

Overview reads on entry and on reattachment of a cached dashboard when Back returns
from another screen. A request arriving during an existing read queues one refresh.
Refresh manually reloads the snapshot. The dashboard does not poll or mutate orders,
supplier status, demand, allocations or reviews. On a read failure, statistics display
em dashes and the recent table is cleared; sanitized database messages explain the
failure and Refresh remains available. Demo values are not used as a live fallback.

Open Orders & Activity for all orders and product details. Receipt and review stay in
the Buyer workflow; supplier status is managed in Suppliers. No schema changes or new
dependencies are required. Verification uses a disposable local PostgreSQL database,
separate from coffee_supplier_allocation.

Verified on 2026-11-13: 30 Maven tests, 22 PostgreSQL checks, 26 JavaFX workflow
checks and 6 checks in a separate JavaFX process passed. Fixtures covered empty
orders, all-time counts beyond a page/date window, inactive parties, saved totals,
receipt/review and allocation changes, supplier activation, cached Back navigation,
a queued refresh during a slow read, safe connection failure/recovery and explicit
demo/preview behavior. Overview agreed with Orders & Activity, Buyer and Supplier
services. The complete demand/allocation/order/completion/review flow preserved
the expected supply balances.
