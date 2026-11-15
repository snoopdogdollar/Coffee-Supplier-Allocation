# Supply by Coffee Type

Added to Admin Overview on 2026-11-15. Each active coffee product is one category
in a stacked column chart. Each colored segment is one Supplier's contribution.
The number of categories comes from the active catalog, never a fixed count of six.
Adding, deactivating or reactivating a product adds/removes/restores its category.
Historical records are preserved by the existing catalog status operation.

## Quantity and scope

- Default: Remaining supply, from supplier_weekly_supply.remaining_quantity after
  committed allocation. Declared supply is available as a separate display option.
- Select a date within an ISO supply week. The default is the database's current
  week. This week selection is independent of the existing eight-week Supply & Demand
  trend, whose Product/Ending week filters retain their existing behavior.
- Contributions require an active Supplier, active supplier-product offering and
  active coffee product in the selected week. Inactive/offering history remains
  available elsewhere but does not contribute to this current-availability chart.
- Products without eligible supply remain visible with a zero total and category.
- Product units are never summed together. Each unit gets its own stacked chart;
  the unit with the most active products appears first. Empty groups are compact.
- Supplier colors derive from Supplier ID and stay consistent across products/units.
  Segment tooltips show quantity, share percentage and the product total. Total
  quantity badges remain readable even when a category has zero-height bars.
- Duplicate product names are distinguished by product ID. Long catalogs can scroll
  horizontally without dropping products.

All aggregation and tooltip values use BigDecimal. Double conversion is limited to
JavaFX's plotting coordinates. Price, demand/allocation algorithms and review scoring
are not changed by this display.

## Automatic updates

The composition widget reads independently every five seconds while the authenticated
Admin dashboard is the visible scene. It also reloads on Apply/Refresh and on cached
Back navigation from allocation or catalog pages. The widget displays the last
successful database update time. The other overview metrics retain their existing
entry/manual-refresh behavior; polling does not repeatedly read the review history.

One composition read at a time is allowed. Its catalog and contribution rows share
a read-only REPEATABLE READ transaction. Only prepared JDBC SELECT statements are
used. Reads execute off the JavaFX thread. Timers stop on navigation, window closure
and logout; responses are guarded against a changed session.

If values are unchanged, a poll updates the timestamp while preserving chart nodes,
so hovering and horizontal scroll are not reset. Background reads leave filter controls
usable. Pending week edits are applied only by Apply/Refresh. When a read fails, the
widget marks the failure and retries while visible; any prior successful snapshot
retains its last-update time. An initial failure shows no chart data and never substitutes
Demo values. Demo Mode uses explicitly labeled session catalog/supply data.

No schema migration, new table or runtime dependency is required.

## Verification

54 regular Maven tests passed, including five new composition/model tests and loading
all 30 FXML views. One opt-in PostgreSQL integration test passed in a new disposable
database, covering six catalog products, multiple Suppliers, exact remaining/declared
totals, real allocation confirmation, zero-supply products, product activation, inactive
Suppliers/offerings, week isolation and separate units. Catalog changes preserve saved
weekly-supply rows.

25 additional JavaFX checks passed: 21 connected workflow/lifecycle checks and four
connection-failure checks. The connected run observed automatic quantity changes after
allocation without pressing Refresh; automatic new/deactivated categories; adding a
product through the actual Admin catalog screen and returning via Back; mixed-unit
groups; stable Supplier colors; unchanged-chart node preservation; and paused polling
when navigating away or logging out. Rendered six-product and mixed-unit states were
visually inspected with synthetic data.

Regular command: `mvn test`. For the integration fixture, create a **new empty** database
named coffee_composition_test, set DB_URL/DB_USER/DB_PASSWORD and
COFFEE_COMPOSITION_INTEGRATION=true, then run
`mvn -Dtest=SupplyCompositionIntegrationTest test`. Each invocation requires a fresh
database; the fixture applies the existing schema and inserts synthetic records.
