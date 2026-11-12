# Project decisions

## 2026-11-07 — Phase 7.2 supplier reviews

- The shop identified by orders.shop_id can submit a review after the order reaches COMPLETED.
- Each order accepts one review through SupplierReviewService. The user selected this policy on 2026-11-07.
- The three existing Boolean fields represent satisfactory quality, correct quantity, and on-time delivery as reported by the shop.
- Supplier identity comes from the locked order. The caller supplies shop and order IDs.
- Submission uses one READ COMMITTED transaction. It locks the order with FOR UPDATE, checks for existing reviews in a subsequent query, inserts, then commits.
- All application review submissions use this service. Concurrent submissions serialize on the same order row.
- Existing reviews remain readable, including historical orders that already contain multiple reviews.
- Menu 11 supports submission and viewing by shop and order ID. The console uses manual shop selection as the current demo context.
- Review comments are optional and limited to 255 Unicode characters.

## 2026-11-07 — Phase 7.3 supplier review history

- Menu 12 reports the number of reviewed orders and the proportions of satisfactory quality, correct quantity, and on-time delivery reported by shops.
- Each order contributes its first eligible review, ordered by created_at and then review_id. This applies the one-review policy to legacy orders with multiple reviews while preserving their stored rows.
- Eligibility requires the order's current status to be COMPLETED, its supplier to match the review, and the review timestamp to fall between order creation and the selected cutoff, inclusive.
- Rates use the count of contributing orders as the denominator. A supplier with no eligible reviews displays N/A. All suppliers are included, including inactive suppliers.
- These metrics summarize shop feedback. Delivery timestamps and status history are absent from the current schema, so the report cannot reconstruct past status transitions or independently measure delivery punctuality.
- Existing timestamps are PostgreSQL TIMESTAMP without time zone. The cutoff uses that same local clock convention and the report shows the database session time zone. Connections used to write and report timestamps must use the same clock convention.
- An empty cutoff uses LOCALTIMESTAMP from the database. Explicit cutoffs use local date/time, are truncated to microseconds, and must be at or before the current database time.
- The service reads the clock and summary in one read-only REPEATABLE READ transaction.

## 2026-11-10 — JavaFX Buyer integration

- Coffee Supplier Allocation owns the backend and JavaFX source in one Maven build. The JavaFX Buyer adapter calls the existing demand transaction and uses the same PostgreSQL schema as Swing and the console.
- The PostgreSQL test workspace uses an explicitly configured Buyer account and shop ID. It saves demand and reads the latest 20 demands for that shop. Other JavaFX workflows use the demo services.

## 2026-11-11 — JavaFX weekly allocation preview

- A separately configured PostgreSQL test Admin can view the batch preview for all open, unallocated demand lines in the selected delivery week. The workspace uses the existing shared allocation service and displays supplier proposals per line.
- Loading the preview uses a read-only transaction.

## 2026-11-11 — JavaFX weekly confirmation

- The configured test Admin can confirm every fully supplied line in the displayed weekly preview. The JavaFX action calls AllocationConfirmationService with the exact snapshot and ISO week shown.
- Each confirmation requires an explicit dialog approval. The UI invalidates the preview after each write attempt and blocks repeated confirmation and window closure while the write runs.
- The backend transaction owns revalidation, supply deduction, allocations, orders and demand status. The JavaFX result lists committed order IDs and fully allocated demand IDs.

## 2026-11-11 — Buyer orders and review integration

- The PostgreSQL test Buyer reads paginated orders scoped to its configured shop, with persisted order items, prices, statuses and review existence.
- Buyer receipt uses the existing PENDING-to-COMPLETED service. Review submission uses the existing completed-order transaction and one-review-per-order policy.
- JavaFX database operations run on workers and refresh the persisted list after a successful write. Cards show order details and prices at two decimal places.

## 2026-11-11 — Supplier weekly supply integration

- The configured PostgreSQL test Supplier reads and writes supply for its own supplier ID and active supplier-product links.
- New records use the existing unique supplier-product/week constraint. Editing preserves the record's product and ISO week and recomputes remaining quantity after locking the latest supply row.
- Declared quantity cannot fall below the committed quantity. Deletion requires zero committed quantity and no allocation history. Existing order and allocation price snapshots are preserved.

## 2026-11-12 — Supplier incoming orders

- Supplier My Orders uses the configured supplier identity and the shared paginated order query. The query includes Buyer identity, persisted item prices and current order status.
- The Supplier reads order status and refreshes after Buyer receipt confirmation. Buyer owns the existing PENDING-to-COMPLETED transition.
