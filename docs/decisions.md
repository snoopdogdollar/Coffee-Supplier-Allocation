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
- The preview uses a read-only transaction. Confirmation and the resulting order workflow remain in the existing Swing and console entry points.
