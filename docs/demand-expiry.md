# Demand expiry

Approved 2026-11-15. EXPIRED and CANCELLED are separate demand statuses.

- OPEN: remaining demand may be allocated in its required ISO week or in advance.
- ALLOCATED: all demand lines have confirmed allocations.
- EXPIRED: the delivery week has ended with some requested quantity unfulfilled.
- CANCELLED: an explicit cancellation, distinct from the passage of time. This change
  preserves existing cancelled records; it does not introduce a cancellation action.

The week runs Monday–Sunday in Asia/Ho_Chi_Minh. A past ISO delivery week closes at
00:00 on the following Monday. ISO week-numbering years and week 53 are respected.
Before the deadline, lack of supply leaves demand OPEN; expiry is not immediate.
There is no carry-over to next week's supply. A Buyer must submit a new demand for
a new delivery week. Demand creation, matching, allocation preview and confirmation
reject a past delivery week, including a preview confirmed after the deadline.

Only overdue OPEN demands containing an unfulfilled product line become EXPIRED.
Confirmed allocations, supply balances, existing orders and reviews are untouched.
A partially allocated demand can expire while its existing orders remain PENDING or
COMPLETED. Buyer receipt and review remain available after expiry. Fully fulfilled
or empty legacy OPEN records are not reclassified by this maintenance operation.
Historical requested quantities remain in the weekly demand chart; CANCELLED
records retain the chart's existing exclusion rule.

## Processing and display

The JavaFX app checks every 60 seconds during a connected configured workspace
session. No database connection is opened by this monitor in Demo/signed-out mode.
Buyer reads first catch up expiry for their shop; Admin Overview, matching and
allocation also catch up before reading. The Swing open-demand list catches up too.
Expiry uses the database wall clock converted explicitly to the business timezone,
not the operating system timezone or transaction-start date.

This MVP runs maintenance inside the desktop app. While every app is closed, no
background server job runs: persisted statuses catch up on the next relevant access.
The date guard still blocks a past-week allocation even if a stored status is stale.
Buyer views refresh on entry/manual Refresh, rather than refreshing visible rows
automatically every minute. Demo's session-only submission model is unchanged.

Expiry runs in a short READ COMMITTED write transaction, locking demand IDs in the
same order as allocation confirmation. A separate update statement rechecks
confirmed allocations after waiting for concurrent locks. Subsequent dashboard reads
retain their read-only repeatable-read snapshots. Retries are idempotent. Background
failures are logged without credentials and retried; foreground failures are shown
through the existing database error messages, with no Demo fallback.

Buyer tables use amber EXPIRED badges and gray CANCELLED badges. Demand Details
explains the cause, labels closed quantities Unfulfilled and keeps confirmed supplier
allocations and View My Orders accessible. No EXPIRED order status is introduced.
The existing VARCHAR(20) demand status column accommodates EXPIRED; no migration,
new table, dependency or scoring/allocation algorithm change is required.

## Verification

`mvn test` includes deadline/timezone, ISO year/week-53 and invalid-week tests, plus
the existing JavaFX FXML/navigation and validation suite.

The opt-in PostgreSQL fixture requires a **new empty** local test database named
`coffee_demand_expiry_test`, DB_URL/DB_USER/DB_PASSWORD and
`COFFEE_DEMAND_EXPIRY_INTEGRATION=true`. Run
`mvn -Dtest=DemandExpiryIntegrationTest test` against that database only.
It checks scoped/global catch-up, current/future/cancelled/allocated preservation,
partial demand expiry, idempotency, unchanged supply/order/allocation balances,
late receipt/review, historical chart totals, rejected past-week operations and
expiry waiting for a concurrent fulfilment transaction before checking allocations.

Verified locally: 58 regular Maven tests passed (six opt-in database fixtures skipped),
the expiry PostgreSQL fixture passed separately, and 15 JavaFX expiry/cancellation
checks passed. All 30 FXML views loaded; rendered demand list/detail screens were
inspected with both statuses visible.
