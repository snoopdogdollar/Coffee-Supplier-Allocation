# PostgreSQL Suppliers and Coffee Shops

Start Desktop/java with the existing DB_URL, DB_USER, DB_PASSWORD and
COFFEE_TEST_ADMIN_PASSWORD settings. Sign in as admin@coffeemongers.test and open
Suppliers or Coffee Shops. The configured Admin in its Admin workspace reads and
writes PostgreSQL. Demo Admin navigation retains its existing demo behavior.

Both catalogs list active and inactive records with ID, name, contact person, phone,
email, address and status. Search matches IDs and contact details. Name is required
(100 characters). Contact person (100), phone (20), email (100) and address (255)
are optional contact information; empty optional fields are stored as NULL. The
existing schema permits shared names and emails, so records are distinguished by ID.

Adding a supplier or shop creates a business record, not a login account. Test
accounts retain their existing mappings. To use a newly created record with the
test Supplier or Buyer login, set COFFEE_TEST_SUPPLIER_ID or COFFEE_TEST_SHOP_ID
to that record's ID in the terminal before restarting the app, together with its
existing test password setting. This does not create persistent authentication.

Deactivate changes only is_active. Inactive suppliers cannot add offerings or edit
weekly supply through the existing services, and their supply is excluded from new
allocation previews. Inactive shops cannot submit new demands and their demands are
excluded from new previews. Reactivating restores eligibility when the other product,
offering, date and quantity conditions are met. Existing demands, supply quantities,
allocations, orders and reviews are retained. Existing order and review services
continue to enforce their own ownership and lifecycle rules.

Status changes check the displayed status and reject stale requests. Database work
runs outside the JavaFX thread; controls are disabled while requests are pending.
If a write result cannot be verified, refresh before adding again. No automatic
retry or database Undo is registered. The fixed catalog enum selects the two SQL
tables; user input is always bound through PreparedStatement.

No schema changes or extra dependencies are required. Orders & Activity also reads
PostgreSQL; supply monitoring, supplier-product administration, reviews and overview
retain their demo labels. Verification uses a disposable local PostgreSQL
database and does not modify the user's coffee_supplier_allocation database.

Verification on 2026-11-12: all 18 Maven tests and 34 PostgreSQL checks passed.
JavaFX checks covered both Admin routes, inserts and input errors, search, Cancel,
Activate and Deactivate, and the management-tab redirects. The business-flow
verification passed 15 checks, followed by 10 checks in a separate JavaFX process.
Screenshots checked both catalogs with 720px scenes and visible add actions. The
new business records completed a demand -> allocation -> order -> completion ->
review flow; deactivation preserved orders, reviews and committed supply quantities.
