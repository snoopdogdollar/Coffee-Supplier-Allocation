# Project decisions

## 2026-11-22 — Selectable console weeks and a rolling demo fixture

- Console startup uses the current ISO week/year in `Asia/Ho_Chi_Minh`. Menu 13
  changes the selected delivery period without renumbering existing actions.
  Supply, demand, matching and allocation paths all use that selection. Historical
  weeks remain selectable for viewing; existing backend expiry guards still apply.
- `DeliveryWeek` validates the ISO year and the actual maximum week for that year.
  Blank input/EOF cancels a selection, and invalid input is retried. A rejected
  past-week operation returns to the menu instead of ending the console.
- Preserve the historical `02_seed_data.sql` unchanged. Add
  `04_current_demo_data.sql` as an alternative for a new empty demo database,
  deriving current/next week from the database business clock, not fixed years.
  It uses returned identity IDs, one transaction and locked empty-table checks.
- No schema, allocation ordering, all-or-nothing policy, expiry deadline, saved
  business data or historical record is changed. The rolling fixture is not a
  migration or live-data update. See [current-demo.md](current-demo.md).

## 2026-11-15 — Form validation and consistent workspace feedback

- Prioritize Create Demand and Weekly Supply: put validation by each field, focus
  and reveal the first invalid field, and validate decimal/date/note input before
  review or saving. Follow existing service limits and allocated supply minimums.
- Add recoverable Buyer catalog loading, error and empty states with Retry products.
  Show busy action labels, explicit success/error messages and guarded submissions.
- Use shared workspace spacing, typography, button sizes and feedback styles across
  Buyer, Supplier and Admin; distinguish failed table reads from empty results.
- Validate the full Supplier → Buyer → Admin allocation → receipt/review workflow
  on isolated local PostgreSQL data. See ui-ux-polish.md for scope and verification.

## 2026-11-15 — Keep the window stable during navigation

- Reuse each window's existing JavaFX Scene and replace only its root content.
  Apply the initial 1280×800 scene size only when creating a scene for the first time.
- Keep window size, position, maximize/fullscreen state and user-controlled resizing
  across sidebar navigation, details, Back, role previews and sign-in/sign-out.
- Cache prior view roots for Back, retaining form state. Refresh restored dashboards
  through a shared display callback. Remove detached scene listeners and stop/restart
  the Admin supply-chart polling when its content leaves/returns to the scene.
- Verify navigation geometry with the existing FXML workflow checks and a separate
  native-window check covering manual resizing, Back, maximize and fullscreen.

## 2026-11-15 — EXPIRED is distinct from CANCELLED

- Close overdue OPEN demand remainders as EXPIRED after their ISO delivery week,
  using Monday 00:00 in Asia/Ho_Chi_Minh as the deadline. Preserve CANCELLED as an
  explicit cancellation; insufficient supply before the deadline leaves demand OPEN.
- Preserve existing confirmed allocations/orders and allow late receipt/review.
  Never roll unmet demand into the following week. Block new past-week demand and
  allocation, including confirmation of stale previews.
- Run minute-based maintenance during connected desktop sessions and catch up on
  relevant reads. No server scheduler runs while all desktop apps are closed.
- Show separate EXPIRED/CANCELLED badges and explanations. See demand-expiry.md.

## 2026-11-15 — Dynamic coffee-type/Supplier stacked chart

- Show one category per active coffee product and stack each eligible Supplier's
  contribution. Default to remaining supply after allocation for a selected ISO week;
  offer declared supply as a display toggle. Products with no supply retain zero totals.
- Keep Supplier colors stable and show exact quantities/share percentages in tooltips.
  Separate different quantity units into individual charts.
- Reflect active catalog additions/status changes automatically. Keep historical rows.
- Refresh the composition widget every five seconds only while Admin Overview is visible,
  and on entry/manual refresh. Display update timestamps and retry failures without a
  Demo fallback. Keep the existing overview/trend/ranking services and algorithms intact.
- See supply-composition-chart.md for behavior and verification.

## 2026-11-15 — Supplier workspace

- Unify Overview, My Products, Weekly Supply and Incoming Orders with a shared Supplier
  sidebar and scoped styling consistent with Buyer.
- Show selected-week supply by product/unit and all-time incoming-order counts; never
  sum unlike quantity units. Read the dashboard in one scoped repeatable-read snapshot.
- Preserve existing product offering and weekly supply write services. Explain declared,
  allocated and remaining quantities, including committed minimums and price units.
- Incoming orders are read-only for Supplier. Buyer confirms receipt and submits review.
  Remove the old Demo-only Mark delivered button while retaining legacy service APIs.
- Verify the Supplier → Buyer → Admin allocation → Buyer receipt/review flow on an
  isolated database. See supplier-workspace.md for behavior and verification.

## 2026-11-14 — Buyer workflow and interface

- Buyer submits demands and follows confirmed allocations through My Demands and My
  Orders. Admin runs and confirms allocation. Remove the Buyer Matching Results route
  and manual supplier-selection step.
- Show requested, confirmed allocation and remaining quantity for each demand item,
  preserving its unit. Allocation sums are independent of the number of linked orders.
- Add shop-scoped all-time dashboard counts and paginated My Demands. Read snapshots
  use repeatable read and background workers. Existing receipt and binary review
  services remain responsible for order completion and review validation.
- Demo submission stops at submitted without creating orders. Keep the legacy matching
  implementation unlinked; do not change the Admin algorithm or schema.
- See buyer-workspace.md for screen behavior and verification.

## 2026-11-14 — Weekly supply and demand dashboard chart

- Show two adjacent bars per ISO delivery week: declared supply from initial_quantity
  and requested demand from demand_items, excluding cancelled demands.
- Use one selected product/unit. Include saved historical records for inactive parties
  and offerings; requested totals include already allocated demands.
- Default to eight weeks ending in the current database ISO week. Permit product and
  ending year/week selection, fill absent weeks with zero, and handle ISO year rollover.
- Load through the overview's existing read-only snapshot. Demo values remain labeled
  and separate. No schema, allocation algorithm or supplier ranking change.
- See weekly-supply-demand-chart.md for behavior and verification.

## 2026-11-14 — Review-based dashboard supplier ranking

- Dashboard score uses all eligible binary reviews: Quality 40%, Quantity 30%, On-time 30%.
- Rates divide true counts by reviewed-order count, on a 0–100 scale. No-review suppliers
  have no score; 1–4 reviews are labeled limited data. Counts appear beside Top scores.
- Top Suppliers contains at most three active suppliers with at least five valid reviews.
  Order by exact score descending, review count descending, supplier ID ascending.
- Reuse the Phase 7.3 first-eligible-review-per-completed-order policy and database cutoff.
- Inactive suppliers remain in the all-supplier performance table, excluded from Top.
- The dashboard reads one repeatable-read snapshot; no schema, allocation algorithm or
  historical run score changes. Price and weekly supply stay separate from this ranking.
- See supplier-ranking.md for formula, UI behavior, verification and limitations.

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
