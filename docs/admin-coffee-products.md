# Admin Coffee Products

Start Desktop/java with DB_URL, DB_USER, DB_PASSWORD and the existing
COFFEE_TEST_ADMIN_PASSWORD setting. Sign in as admin@coffeemongers.test, then open
Coffee Products. The configured Admin in its Admin workspace accesses PostgreSQL;
the demo Admin retains its demo catalog. Other management sections and the overview
remain labeled as demo data.

The shared catalog lists active and inactive products with ID, name, unit, variety
and description. Search filters locally. Add Product saves a new active product;
name (100 characters) and unit (20) are required. Variety (50) and description (255)
are optional. Names need not be unique in the existing schema; select by product ID.

Activate / Deactivate changes only coffee_products.is_active. It does not delete
or rewrite supplier links, supply quantities, demands, allocations, orders or reviews.
Inactive products are excluded when Buyer and Supplier catalogs or allocation previews
are reloaded. Submitting a stale Buyer choice or Supplier offering is also validated
by the existing backend. Reactivating a product restores eligibility for existing
active supplier links and eligible weekly supply.

Status changes check the status displayed to the Admin. A stale status is rejected
with a refresh instruction. Catalog I/O runs outside the JavaFX thread; write controls
are blocked during requests. If a write result cannot be verified, refresh before
adding again. There is no automatic retry or database Undo.

No schema migration or new dependencies are required. Verification runs against a
disposable local PostgreSQL database, separate from coffee_supplier_allocation.

Verified on 2026-11-12: 15 Maven tests, 21 PostgreSQL checks, 16 JavaFX write-flow
checks and 7 checks after reopening a separate JavaFX process passed. The FXML
loading test was rerun after the final status-badge adjustment. Checks covered
Unicode and quoted input, status conflicts between two connections, role guards,
Supplier and Buyer catalogs, allocation eligibility and preservation of history.
