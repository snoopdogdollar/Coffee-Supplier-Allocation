# Supplier Products in the PostgreSQL test workspace

Start Desktop/java with the existing DB_URL, DB_USER, DB_PASSWORD,
COFFEE_TEST_SUPPLIER_ID and COFFEE_TEST_SUPPLIER_PASSWORD settings. Sign in as
supplier@coffeemongers.test. Products and Manage Products on the supplier dashboard
now read and update that supplier's database catalog.

- Add Product links an active shared coffee product to the configured supplier.
  Repeating an add is harmless; adding a stopped offering reactivates the same link.
- Product IDs and units distinguish shared products with identical names.
- Weekly Supply reads the active offerings when opened or refreshed. Add quantity
  and price there before expecting that product to appear in allocation proposals.
- Stop offering sets supplier_products.is_active to false. New allocation previews
  exclude its supply. Supply records, allocations, orders and reviews are retained.
  Adding the same product again resumes the offering and retains existing supply.
- All writes are scoped by supplier ID in the service and DAO. Catalog reads and
  writes run outside the JavaFX thread. This uses the existing 13-table schema.

The demo workspace keeps its in-memory catalog and Undo behavior. PostgreSQL writes
require Refresh if their outcome could not be verified; Undo does not revert them.

Verification uses a disposable local PostgreSQL database and JavaFX smoke flows.
It does not modify the user's coffee_supplier_allocation database.
