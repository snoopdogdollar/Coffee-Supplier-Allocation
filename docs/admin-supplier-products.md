# PostgreSQL Admin Supplier Products

Use the existing DB_URL, DB_USER, DB_PASSWORD and COFFEE_TEST_ADMIN_PASSWORD settings.
Sign in as admin@coffeemongers.test and open Supplier Products. The existing management
tab links to the same screen. Demo Admin keeps its demo catalog and navigation.

Every supplier-product link is listed, including stopped links and inactive suppliers
or products. A weekly supply record is not required to appear. Columns identify the
link, supplier and product IDs/names, unit, offering status, parent statuses and whether
any weekly supply record exists. Declared refers to any week and does not imply that
quantity is currently available. Use Supply Monitoring for quantities and dates.

Supplier and product filters are literal, case-insensitive ID/name substring searches.
Blank fields include all links. Search / Refresh reloads the first 20 links in descending
link-ID order. Load More uses the last applied filters; the status says when all matching
links are loaded. Duplicate names remain distinct through IDs and product units.

Offer Product requires an active supplier and coffee product from the dropdowns. It
reuses SupplierProductService: the unique supplier/product pair is inserted or reactivated.
Repeated offers of an already active pair are harmless. After success, the table is
filtered to the chosen supplier/product names so the saved pair can be inspected.
It does not declare quantity, create an order or create a login account.

Stop selected offering confirms the exact supplier/product IDs and delegates to the
existing scoped supplier service. It sets supplier_products.is_active to false rather
than deleting records. Supply, allocations, orders and reviews are retained. New
allocation previews exclude stopped offerings. Offer the same pair again to resume
the existing link and its eligible weekly supply. Existing active-supplier guards are
preserved; reactivate an inactive supplier in Suppliers before editing its offerings.

The read snapshot includes the page and active dropdown catalogs. JDBC runs outside
the JavaFX thread. Controls and window-close requests are blocked during pending
requests; database writes register no Undo or automatic retry. If a result cannot be
verified, refresh before retrying. Backend ownership checks and unique constraints
remain authoritative for stale selections and concurrent requests.

No schema changes or new dependencies are required. Verification uses a disposable
local PostgreSQL database and does not modify coffee_supplier_allocation.

Verified on 2026-11-13: 27 Maven tests, 27 PostgreSQL checks, 24 JavaFX workflow
checks and 7 checks in a separate JavaFX process passed. Fixtures covered pagination,
literal filters, same-name products with different IDs/units, links without supply,
inactive parents, ownership rejection and concurrent duplicate offers. The Admin
offering reached the configured Supplier's Weekly Supply form, produced an allocation,
completed order and review, and retained its supply and history through stop/resume.
The Offer Product action was checked in a 720px scene.
