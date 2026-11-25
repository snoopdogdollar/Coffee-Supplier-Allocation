# Forms and workspace feedback

Updated 2026-11-15. This pass focuses on Create Demand and Weekly Supply, with shared
typography, spacing, button sizing and table styles across Buyer, Supplier and Admin.
The stable-window navigation from the preceding change remains in use.

## Forms

- Create Demand shows errors next to the delivery date, product, quantity, product
  note and additional notes. Duplicate products are explained on the affected row.
  Notes follow the existing 255-code-point limit; quantities follow DECIMAL(15,2).
- Weekly Supply shows product, week, quantity and price errors inline. Quantity and
  price retain existing connected/Demo service rules; allocated quantities establish
  the minimum declared quantity when editing. The selected product's units remain
  visible. Allocated supply has no enabled Delete action.
- Errors use text and a field border, clear on editing and provide accessible help.
  The first invalid control receives focus and is scrolled into view. Date validation
  parses the editor text rather than relying only on the stored DatePicker value.
- The Buyer product catalog shows loading/error/empty feedback and offers Retry
  products after failure or an empty result. Retry is guarded while loading.
- Busy write actions use Submitting… / Saving… labels, loading indicators and existing
  in-flight guards. The review step still precedes demand submission. Save supply
  becomes Save changes when editing; deletion stays disabled without a selection.

## Shared states and appearance

ViewFeedback supplies explicit information, loading, success and error states with
consistent text colors and a compact loading indicator. The indicator is removed
on completion/failure. These states are used by the two forms, Demand Details,
Buyer Overview, My Demands, Supplier Overview and connected Admin Overview.

Tables distinguish loading, a successful empty read and an unavailable read with a
Refresh instruction. Failed connected workspaces do not show Demo data. Existing
ownership checks and service transactions still handle persistent operations.

Shared workspace CSS aligns content/card spacing, headings, table rows, buttons and
form controls. DatePicker/ComboBox padding avoids doubling the editor's padding.
Focus and invalid-field borders are visible at the existing minimum window size.

## Verification

- The regular Maven suite loads all 30 FXML views and exercises Buyer, Admin and
  Supplier navigation, including fixed footer visibility and stable window bounds.
- 34 native JavaFX/PostgreSQL workflow checks passed against a new isolated local
  database named coffee_ux_polish_test. They cover inline errors, decimal precision,
  duplicate products, error correction, repeated write handlers, saved feedback,
  minimum-size footer fit and the full Supplier supply → Buyer demand → Admin
  allocation → Buyer receipt → Boolean review flow. One demand/order/review was
  persisted and supply was deducted exactly once.
- Three additional focus/scroll checks and 14 disconnected-workspace checks passed.
  The latter verify catalog retry, disabled unavailable actions, cleared indicators,
  explicit failure placeholders and no Demo fallback across all three roles.
- Rendered validation, saved-demand, supply and Admin screens were inspected.

Use `mvn test` for the regular suite. The native walkthrough used configured test
accounts with an isolated database; no production database was used for verification.
