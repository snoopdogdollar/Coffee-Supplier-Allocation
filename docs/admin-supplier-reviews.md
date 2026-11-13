# PostgreSQL Admin Supplier Reviews

Use DB_URL, DB_USER, DB_PASSWORD and the existing COFFEE_TEST_ADMIN_PASSWORD setting.
Sign in as admin@coffeemongers.test and open Supplier Reviews. The Reviews management
tab links to the same view. Demo Admin retains its demo behavior; configured Admin
access requires its Admin workspace.

Reviews show review/order IDs, the owning coffee shop and supplier IDs/names, all
three Boolean criteria, review creation time and current order status. Select a row
to read its full comment and timestamp. Null comments display No comment. Parent
inactivity does not hide historical reviews. Creation time is the review timestamp;
the on-time criterion is the Buyer's submitted feedback. No delivery timestamp or
star rating is inferred or generated.

Supplier and shop filters are literal case-insensitive ID/name substring searches;
Order ID is an exact positive ID, optional. Blank filters include all reviews across
all dates. Search / Refresh reloads the first 20 matching reviews, newest review ID
first. Load More appends the next page using the last applied filters, and the status
explains whether more reviews remain. Invalid IDs, cursors and overlong/unsupported
filter text are rejected before querying.

This is a read-only Admin monitor. Review creation, ownership checks, the completed
order requirement and duplicate-review rejection remain in SupplierReviewService and
the existing Buyer workflow. There are no Admin edit/delete actions or score changes.
The query joins the exact order/supplier pair and reads shop ownership from that order.
All JDBC reads run outside the JavaFX thread using read-only repeatable-read snapshots.
No schema changes or extra dependencies are required. The Admin Overview remains demo.

Verification uses a disposable local PostgreSQL database, separate from
coffee_supplier_allocation.

Verified on 2026-11-13: 30 Maven tests, 48 PostgreSQL checks, 13 JavaFX workflow
checks and 6 checks in a separate JavaFX process passed. Fixtures covered multiple
pages, exact order IDs, combined/literal filters, null and 255-character Unicode
comments, timestamps, mixed Boolean criteria, inactive parents, role guards and
Buyer ownership/lifecycle validation. A Buyer completed an order and submitted its
review through the actual form; Admin Refresh showed the same values. Monitoring
preserved allocations and supply quantities. The paging action and full-comment
area were checked in a 720px scene.
