# Project decisions

## 2026-11-07 — Phase 7.2 supplier reviews

- The shop identified by orders.shop_id can submit a review after the order reaches COMPLETED.
- Each order accepts one review through SupplierReviewService. The user selected this policy on 2026-11-07.
- The three existing Boolean fields represent satisfactory quality, correct quantity, and on-time delivery as reported by the shop.
- Supplier identity comes from the locked order. The caller supplies shop and order IDs.
- Submission uses one READ COMMITTED transaction. It locks the order with FOR UPDATE, checks for existing reviews in a subsequent query, inserts, then commits.
- All application review submissions use this service. Concurrent submissions serialize on the same order row.
- Existing reviews remain readable, including historical orders that already contain multiple reviews.
- Menu 11 supports submission and viewing by shop and order ID. The console uses manual shop selection as the current demo context.
- Review comments are optional and limited to 255 Unicode characters.
