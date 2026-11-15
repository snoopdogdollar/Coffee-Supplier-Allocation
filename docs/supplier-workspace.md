# Supplier workspace

Updated on 2026-11-15. Supplier screens now share the cream/brown/green presentation
used by the Buyer workspace, with a common sidebar: Overview, My Products, Weekly
Supply and Incoming Orders. Larger text, clear page headings, fixed action footers
and visible quantity units replace the older mixed navigation. Supplier styles are
scoped; Admin and Buyer screens retain their existing presentation.

## Screens and data

- Overview shows active offered-product count, the number of saved supply rows in the
  selected week, and all-time PENDING/COMPLETED order counts. A product-by-product table
  separates declared, allocated and remaining quantities and unit price. Quantities
  with different units are never summed. Another table shows at most five latest
  incoming orders, by descending order ID, with the Buyer, saved order date and status.
- The week picker defaults to the current database ISO week; Apply or Refresh loads
  that week's supply. Changing the supply week does not narrow the all-time order counts.
- My Products adds existing central coffee products or stops an offering using the
  existing SupplierProductService. Already-offered products are excluded from the Add
  picker. Saved supply and orders remain in PostgreSQL when an offering is stopped.
- Weekly Supply preserves the existing create/edit/delete services. It adds an
  Allocated column, calculated as declared minus remaining, and explains the selected
  product's quantity and price units. The edit notice shows the minimum committed
  amount. Product/week stay fixed when editing a saved record. Existing backend rules
  reject lowering declared supply below commitments or deleting allocation history.
- Incoming Orders retains 20-order keyset pagination and product/price snapshots,
  with Buyer identity and receipt/review status. It is read-only in both live and Demo
  views: the old Demo-only Mark delivered action has been removed from the Supplier UI.
  Buyer owns receipt confirmation, then submits the three Boolean review criteria.

SupplierOverviewService reads catalog, weekly rows, all-time totals and recent orders
in one read-only REPEATABLE READ transaction. SupplierOverviewDao binds supplier ID
through PreparedStatement. The ID comes from the existing configured SupplierSupplyService
adapter; this update does not add production authentication. Existing active-supplier
validation for catalog/supply remains in force. Saved weekly rows for stopped offerings
remain visible as history, even though they cannot supply new allocation previews.

Reads run off the JavaFX thread. Overview reloads on entry, cached Back navigation and
Refresh; the other pages have explicit Refresh controls and reload when opened. Errors
clear stale read results and permit retry, with no live-to-Demo fallback. Further order
page failures preserve the displayed rows. Existing service transactions and write
confirmation dialogs are retained. Admin workspace previews cannot edit product/supply.

Demo Mode remains session-only and visibly labeled. Its supply picker now uses the
Supplier's offered products. No new schema, table, runtime dependency, allocation
algorithm or review formula is introduced.

## Verification

49 regular Maven tests passed, including three new Supplier validation/decimal tests,
all 29 FXML files and Supplier navigation. The Save footer was checked at 720px window
height. The database integration tests are opt-in and skipped in a regular test run.

One new PostgreSQL integration test passed in a disposable database:
Supplier adds products → declares supply → Buyer submits demand → Admin confirms
allocation → Buyer confirms receipt and submits a binary review. It checks exact
remaining quantities, mixed units, supplier isolation, minimum declaration/delete
protections, all-time totals beyond a page, stopped-offering history, incoming order
pagination and an empty Supplier account. Foreign Supplier orders remain separate.

43 additional JavaFX checks passed: 28 normal workflow/preview checks, 10 connection
failure checks and five empty-account checks. They cover product addition/stopping,
supply creation/edit/deletion, allocated-supply rejection, units, week filtering,
order pagination and updated totals after Buyer receipt/review. Four rendered screen
images were visually inspected using synthetic data.

Run `mvn test` for regular tests. For the integration fixture, use a **new empty**
database named `coffee_supplier_workspace_test`, configure DB_URL/DB_USER/DB_PASSWORD,
set `COFFEE_SUPPLIER_WORKSPACE_INTEGRATION=true`, then run
`mvn -Dtest=SupplierWorkspaceIntegrationTest test`. Each fixture run needs a new empty
database. It creates the existing schema and synthetic rows, never application data.
