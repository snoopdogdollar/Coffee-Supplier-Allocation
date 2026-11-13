# Dashboard supplier ranking

Approved on 2026-11-14. This is a supplier-wide summary of binary shop feedback,
using all eligible review history. Price and weekly capacity remain separate
criteria for product/week allocation; allocation algorithms and saved run scores
are unchanged.

## Rates and score

Each order contributes its first eligible review, ordered by `created_at`, then
`review_id`. Reuse `SupplierHistoryDao`: the order must currently be `COMPLETED`,
the review supplier must match the order supplier, and the review timestamp must
be between order creation and the database cutoff, inclusive. Later duplicate
reviews are preserved but do not contribute to the rates or latest-review date.

For each criterion, rate = true count / contributing order count * 100.

Score = 0.40 * quality rate + 0.30 * quantity rate + 0.30 * on-time rate.

Example: 8 quality passes, 9 quantity passes and 7 on-time passes across 10
reviewed orders produce 80%, 90%, 70%, and a score of 80/100. Rates and scores
are displayed to two decimal places. Scores are calculated directly from counts,
before display rounding; ordering compares exact weighted fractions.

## Eligibility and ordering

- Top Suppliers shows at most 3 suppliers, active and with at least 5 valid reviews.
- Rank by score descending, then review count descending, then supplier ID ascending
  to make equal-score/equal-count results repeatable.
- Suppliers with 1–4 reviews retain their score and show `Limited data (<5 reviews)`.
- Suppliers with no reviews have no score/rates/latest review and show `Insufficient data`.
  Their display uses an em dash, never an invented zero score.
- Inactive suppliers retain all history in Supplier Performance but cannot enter Top.
- The all-supplier table uses the same score/count/ID order, with unreviewed suppliers last.

The dashboard shows score, all three rates, review count, latest contributing review,
current supplier status and data eligibility. The cutoff label includes the database
session time zone. These metrics summarize shop reports; they are not independently
measured delivery timing. The schema does not store a completion timestamp/status
history, so eligibility uses the current completed status and existing timestamp rules.

## Loading and failures

AdminOverviewService loads totals, recent orders, database cutoff and supplier history
in one read-only REPEATABLE READ transaction. SupplierRankingService applies the policy
without database or UI dependencies. The controller loads off the JavaFX thread and
renders on that thread. Entry, cached Back navigation and Refresh reload the snapshot.
Starting a read clears previous scores; failures display an unavailable message and
allow retry. Demo workspaces do not read PostgreSQL or show fabricated Top scores.

No schema migration or dependency is required. Review submission remains binary.

## Verification

Verified on 2026-11-14: 38 Maven tests passed, including seven ranking unit tests,
the existing FXML/navigation test, and one opt-in PostgreSQL integration test.
The integration test used a new disposable local database, not application data.
It covers duplicate reviews, non-completed orders, pre-order and future reviews,
cutoffs, zero-review suppliers, inactive history, ties, the five-review threshold
and reloading after supplier deactivation. Ten additional checks loaded the live
dashboard, inspected its nine columns/messages and verified Refresh after activation.
The rendered 1440×1000 dashboard was visually inspected.

Normal command: `mvn test` (integration test skips without the explicit opt-in).
For integration, start an isolated PostgreSQL instance, create a **new empty** database
named `coffee_ranking_test`, set `DB_URL`, `DB_USER`, `DB_PASSWORD`, and set
`COFFEE_RANKING_INTEGRATION=true`. Run
`mvn -Dtest=SupplierRankingIntegrationTest test`. The fixture applies the existing
schema and inserts synthetic rows; use a fresh database for each invocation.
