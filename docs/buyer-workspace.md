# Buyer workspace

Updated on 2026-11-14. Buyer navigation follows:

Create Demand → My Demands → Demand Details → My Orders → Confirm received → Review supplier.

Admin owns running and confirming allocation. A Buyer submits a request and views
the confirmed result. Submitting a demand never invokes supplier recommendations,
confirms an allocation or creates orders. Matching Results and supplier selection
are no longer linked from the Buyer workspace. The legacy matching FXML/controller
and demo service API are retained for existing references/tests, with no Buyer route.

## Screens

- Overview: four all-time metrics for the linked shop: all demands, OPEN demands,
  PENDING orders and COMPLETED orders with no existing review. Show up to five latest
  demands and orders; metrics are not derived from that limited list.
- My Demands: keyset pagination, 20 demands per page, descending demand ID. Each row
  shows products, requested quantities with their individual units, ISO delivery week,
  status and the count of fully allocated product lines.
- Demand Details: fresh shop-owned demand data, requested/confirmed/remaining amounts
  per line, confirmed supplier allocations and saved unit prices. The remaining amount
  is max(requested minus confirmed allocation, zero). CONFIRMED allocation rows count
  once each; multiple orders for an allocation do not multiply quantities. Preview or
  cancelled allocations are excluded. The status displayed is the stored demand status;
  one fully allocated line does not imply that all lines are allocated.
- Create Demand: product/unit entry, required delivery date, visible ISO delivery week,
  notes and a review step followed by Submit Demand. Request creation date and required
  delivery date remain distinct in the Demo presentation model as in the real backend.
- My Orders: allocated suppliers and saved product/price details, receipt confirmation,
  and the existing three Boolean review criteria. Existing pagination and receipt/review
  transactions remain in use. Advertisements and matching instructions are removed.

The shared sidebar provides Overview, My Demands, My Orders and Create Demand. Buyer
styles increase text sizes and distinguish the page title, metrics and actions, using
the existing cream/brown/green palette. These styles are scoped to Buyer screens so
the Admin Overview and weekly chart retain their presentation.

## Data and refresh

BuyerWorkspaceDao reads scoped shop/demand/allocation data through PreparedStatement.
BuyerWorkspaceService owns a read-only REPEATABLE READ transaction for dashboard,
demand page or detail reads. Dashboard totals, recent demands and orders share one
snapshot. The shop ID comes from the existing configured BuyerDemandService adapter.
Demand detail ownership is checked again on every read. This retains the existing
configured-test-account integration; it does not add production identity/authentication.

Database work runs off the JavaFX thread. Overview and My Demands reload on entry,
cached Back navigation and manual Refresh. Detail has its own Refresh. Loading clears
stale dashboard/page data; errors explain failure and allow retry. A disconnected live
workspace never falls back to Demo records. Loading further demand pages preserves
the displayed page if the request fails. Completion and review use the existing Buyer
Order service and refresh the persisted order cards after a successful write.

Demo Mode uses session data and visibly labels it. Its Submit Demand action saves the
request in memory and stops at submitted; no fake allocation or order is generated.
The real Admin confirmation flow remains the way to generate database-backed orders.
Inactive shops/suppliers/products retain readable historical data; existing service
validation continues to control whether new demands can be submitted.

No schema migration, new table, dependency or allocation/scoring algorithm change.

## Verification

46 regular Maven tests passed, including the four new Buyer model/validation tests,
all 28 FXML files and navigation through the new Buyer workflow. The navigation test
verifies that Demo submission stays in Demand Details, creates no order, and leads to
My Demands rather than Matching Results. Existing opt-in chart/ranking database tests
are skipped in the regular run.

One new opt-in PostgreSQL integration test passed in a new disposable local database:
shop ownership, all-time counts beyond a page, keyset pagination, decimal/mixed-unit
line balances, multi-supplier allocation, partial demand fulfillment, one-allocation-to-
many-orders deduplication, inactive history and the actual Admin confirmation → Buyer
receipt → binary review flow. The empty-shop snapshot is also checked.

21 additional JavaFX workflow checks passed against the disposable database, covering
pagination, detail quantities, receipt/review dialogs and demand submission without
creating orders. Four checks in a separate disconnected process confirmed explicit
failure messages, empty live tables and no Demo fallback. Rendered Overview, My Demands,
Create Demand, Demand Details and My Orders screenshots were inspected.

Normal checks: `mvn test`. The database fixture requires a **new empty** disposable
database named `coffee_buyer_workspace_test`, DB_URL/DB_USER/DB_PASSWORD and
`COFFEE_BUYER_WORKSPACE_INTEGRATION=true`; run
`mvn -Dtest=BuyerWorkspaceIntegrationTest test`. Each fixture run requires an empty
database and uses the existing schema with synthetic records, not application data.
