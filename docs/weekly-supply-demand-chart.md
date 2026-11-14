# Weekly supply and demand chart

Implemented on 2026-11-14 in Admin Overview. The chart replaces the illustrative
Mon–Sun columns with two adjacent bars for every ISO delivery week:

- Green: Supply (declared), the sum of `supplier_weekly_supply.initial_quantity`.
- Brown: Demand (requested), the sum of `demand_items.quantity`, grouped by
  `demands.required_year` and `required_week`, excluding `CANCELLED` demands.

Select one product so quantities always share its declared unit; products and units
are never added together. The product selector includes inactive products for history.
Supply includes saved supplier-product records even when their supplier/offering is
currently inactive. Demand includes historical requests from inactive shops and
already allocated demands. These are declared/requested totals, rather than remaining
supply versus outstanding demand. Confirming an allocation does not reduce either
bar simply by consuming remaining supply.

The default window is eight weeks ending in the database's current ISO week/year.
Select Product, Ending ISO year and Ending week, then Apply. Year/week validation
rejects invalid week 53 values and incomplete filters before opening a connection.
The window spans ISO year boundaries and fills missing weeks with zero. At the
minimum supported ISO year 1, the window starts at 0001-01-01 instead of year 0.
Request date and supply creation date do not determine delivery-week placement.

The chart has a zero-based quantity axis labeled with the product unit, a legend,
chronological week labels and exact decimal-value tooltips. Decimal aggregation
uses PostgreSQL NUMERIC and Java BigDecimal; conversion to double is limited to
JavaFX's plotted coordinates. Supply and demand are aggregated independently to
avoid multiplying totals when either side has several rows.

WeeklyMarketDao reads products and weekly totals through prepared JDBC statements.
WeeklyMarketService validates the window and product. AdminOverviewService owns
the same read-only repeatable-read transaction used by totals, recent orders and
supplier rankings. JDBC stays off the JavaFX thread. Refresh/Apply clear old bars
before reading. Failure shows an unavailable message and allows retry; an empty
product list or a window with no records has an explicit explanation.

Demo Mode displays paired weekly sample bars with an explicit demo-only caption;
its filters are disabled and it does not read PostgreSQL. The chart adds no schema,
dependency, allocation policy or ranking formula changes. Other overview metrics
retain their all-time scope. Historical bars reflect currently saved records,
including the current cancellation status, rather than immutable snapshots.

## Verification

42 regular Maven tests passed, including the five new date/filter tests and all
FXML/navigation checks. The FXML check now also captures asynchronous rendering
errors reported on the JavaFX thread. One opt-in PostgreSQL integration test passed
against a new disposable database, covering separate aggregates, decimals, multiple
rows, inactive records, cancellation, delivery-week placement, mixed product units,
ISO year boundaries, zero weeks and remaining-supply changes. The existing opt-in
supplier-ranking database fixture was skipped in this run.

17 additional live/demo dashboard checks passed, including product/unit changes,
invalid weeks, empty weeks, bar rendering and explicit demo behavior. Both rendered
1440×1000 screenshots were visually inspected.

Run normal checks with `mvn test`. For the chart integration test, use a fresh empty
database named `coffee_weekly_chart_test`, configure DB_URL/DB_USER/DB_PASSWORD and
set `COFFEE_CHART_INTEGRATION=true`, then run
`mvn -Dtest=WeeklyMarketIntegrationTest test`. The fixture creates the existing
schema and synthetic records; each invocation requires a new empty database.
