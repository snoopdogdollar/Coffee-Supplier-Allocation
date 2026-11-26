# Database — Coffee Supplier Allocation

Cập nhật **26/11/2026**, đối chiếu DDL và Java services hiện có. Database dùng
PostgreSQL/JDBC, đúng 13 application tables. Chạy app và cấu hình tài khoản theo
[README gốc](../README.md) và [JavaFX launcher](../docs/javafx-launcher.md).

## Các file

| File | Mục đích |
| --- | --- |
| [01_schema.sql](01_schema.sql) | Tạo schema một lần trên database development/test trống; DDL nằm trong một transaction. |
| [02_seed_data.sql](02_seed_data.sql) | Fixture lịch sử cố định tuần 41–43/2026, có handcrafted historical transactions/reviews. |
| [03_test_queries.sql](03_test_queries.sql) | Fixture kiểm tra DDL/constraints có `ROLLBACK`; không phải seed lâu dài. |
| [04_current_demo_data.sql](04_current_demo_data.sql) | Fixture hiện tại/tuần sau từ database clock theo `Asia/Ho_Chi_Minh`; không tạo sẵn orders, allocations hoặc reviews. |
| [VALIDATION.md](VALIDATION.md) | Báo cáo DDL ngày 15/10/2026, không đại diện toàn bộ application hiện tại. |

Chọn **một seed**: `02` hoặc `04`; không chạy cả hai vào cùng database. Cả hai
kiểm tra project tables trống dưới lock, từ chối toàn bộ nếu có data và không
reset/delete. Dữ liệu đã lưu không tự chuyển tuần khi thời gian trôi qua.

## Khởi tạo database demo mới

Trong pgAdmin:

1. Tạo database development/demo **mới và trống**, ví dụ `coffee_supplier_allocation_demo`.
2. Chọn đúng database, mở Query Tool, chạy **toàn bộ** `01_schema.sql`.
3. Chạy **toàn bộ** `04_current_demo_data.sql` để có dữ liệu hiện tại/tuần sau.
   Nếu cần phân tích fixture October 2026, dùng `02_seed_data.sql` thay thế.
4. Dùng ID thực tế từ kết quả seed để cấu hình Buyer/Supplier thử nghiệm.

Với PostgreSQL tools trên PATH, chạy từ repository root:

```powershell
createdb -h localhost -p 5432 -U postgres coffee_supplier_allocation_demo
psql -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_demo -v ON_ERROR_STOP=1 -f .\database\01_schema.sql
psql -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_demo -v ON_ERROR_STOP=1 -f .\database\04_current_demo_data.sql
```

Thay host, port và user theo database development. Nếu tools chưa trên PATH, dùng
đường dẫn đầy đủ trong PostgreSQL `bin` hoặc pgAdmin. Công cụ yêu cầu password
khi cần; không nhúng password vào command/commit.

Database đã có dữ liệu cần cấu hình hiện có hoặc migration được xem xét riêng;
không chạy lại DDL/seed để reset. Nếu pgAdmin đang trong failed transaction,
chạy `ROLLBACK;` trước lệnh tiếp theo rồi xử lý nguyên nhân.

Để kiểm chứng DDL riêng, tạo database test khác, chạy `01_schema.sql` rồi
`03_test_queries.sql`; script báo `PASS` và rollback fixture. Identity sequences
vẫn có thể tăng sau rollback: không giả định IDs bắt đầu từ 1.

Schema dùng identity và stored generated column, yêu cầu PostgreSQL 12+ ở mức
DDL. Báo cáo lịch sử đã chạy PostgreSQL 18.1; không suy ra toàn bộ application
đã được kiểm thử trên mọi version chỉ từ mức tương thích DDL.

## Baseline và quan hệ

Đúng 13 bảng: `coffee_shops`, `suppliers`, `coffee_products`, `supplier_products`,
`supplier_weekly_supply`, `demands`, `demand_items`, `recommendation_runs`,
`supplier_scores`, `allocations`, `orders`, `order_items`, `supplier_reviews`.

So với [sơ đồ gốc](../db_schema.png), DDL có các chi tiết triển khai:

- `demands.required_year`, `required_week` là tuần cần hàng, khác `request_date`.
- `allocations.run_id`, `supply_id` truy vết run và supply. Composite FK
  `(run_id, demand_item_id)` bảo đảm allocation thuộc item của run.
- PK `BIGINT GENERATED ALWAYS AS IDENTITY`; INSERT bỏ ID, dùng `RETURNING`.
- `order_items.total_price` là generated column `round(quantity * unit_price, 2)`.
  Không truyền cột này; tổng vượt precision bị từ chối.
- Composite FK giữ supplier của order khớp allocation và supplier review khớp order.
- DDL vẫn cho **một allocation → nhiều orders**, **một order → nhiều reviews**.
  Service hiện tạo một order/supplier allocation và cho một review/order; không
  nhầm service policy với UNIQUE constraint chưa có trong schema.

Không có bảng inventory, distance, star rating hoặc login trong baseline này.

## Kiểu dữ liệu, đơn vị và thời gian

- Quantity/giá `DECIMAL(15,2)`; Java dùng `BigDecimal`. Demand/allocation/order
  quantity dương; supply/giá không âm; remaining nằm trong `0..initial`.
- Mọi quantity của product dùng `coffee_products.unit`; app không tự chuyển đổi
  hoặc cộng khác unit. Fixture/nhãn console dùng kg, VND/kg; không có multi-currency.
- Week/year theo ISO 8601, bắt đầu thứ Hai. Dùng ISO week-based year, không mặc
  định calendar year; constraint kiểm tra tuần 53 theo từng năm.
- Timestamps là `TIMESTAMP` không timezone. Cần thống nhất convention giữa writer/
  report; kiểm tra `SHOW TIME ZONE` của JDBC session thực tế. Một số service đặt
  UTC trong transaction, nhưng không giả định mọi connection UTC. Review cutoff
  report hiển thị session timezone; expiry chuyển database clock sang business
  zone `Asia/Ho_Chi_Minh` tường minh.
- Contact, description, variety, note, comment optional. Không unique toàn cục cho
  email/phone/tên; business records phân biệt bằng ID.
- Không cascade-delete lịch sử. Deactivate/stop offering giữ dữ liệu; xóa record
  đang được tham chiếu bị foreign key chặn.

## Trạng thái nghiệp vụ hiện tại

Status columns là `VARCHAR(20) NOT NULL` với check không rỗng. Workflow được kiểm
tra bởi services; không có PostgreSQL enum khóa cứng danh sách status.

| Record | Workflow hiện có |
| --- | --- |
| Demand | `OPEN` → `ALLOCATED` khi mọi line fulfilled; OPEN quá hạn còn thiếu thành `EXPIRED`. `CANCELLED` khác expiry; chưa có general cancellation action. |
| Recommendation run / allocation | Confirmation ghi `CONFIRMED`; một run gắn với một demand item. |
| Order | Confirmation tạo `PENDING`; Buyer receipt chuyển `COMPLETED`. |
| Review | Owning shop review completed order, một lần/order qua service. |

`TEST_*` trong DDL tests và status handcrafted của seed lịch sử không định nghĩa
thêm transition cho application.

Expiry xảy ra sau khi tuần giao hàng kết thúc theo business timezone, giữ
confirmed allocations, supply, orders và reviews. Không carry-over; service từ
chối tạo demand/matching/allocation cho tuần quá hạn. JavaFX catch-up 60 giây
khi workspace đủ điều kiện; foreground access cũng catch-up. Không có job server
khi mọi desktop app tắt. Xem [demand expiry](../docs/demand-expiry.md).

## Constraints và transaction service

DDL bảo vệ PK/FK/composite FK, unique supplier-product và offering-week, unique
supplier/run score, giá/lượng/tuần hợp lệ và generated line totals. CHECK một dòng
không tự bảo vệ tổng nhiều allocations, ownership hoặc mọi điều kiện active/
product/week. Service có trách nhiệm giữ các bất biến đó.

`AllocationConfirmationService` đã triển khai:

1. Catch-up expiry riêng, sau đó mở transaction `SERIALIZABLE` cho confirmation.
2. Khóa demand headers/items rồi supply theo thứ tự ổn định.
3. Kiểm tra lại tuần, eligibility, nguồn cung và toàn bộ snapshot đã hiển thị.
4. Tính batch dùng chung supply: all-or-nothing từng line, demand cũ trước,
   supplier rẻ trước, tie-break theo ID; preview cũ bị từ chối.
5. Ghi runs/allocations, trừ remaining bằng guarded update, tạo orders/items và
   cập nhật demand cùng commit; lỗi rollback toàn bộ phần confirmation.

Ví dụ guarded update trong DAO, với parameters bind bằng JDBC:

```sql
UPDATE supplier_weekly_supply
SET remaining_quantity = remaining_quantity - ?
WHERE supply_id = ? AND remaining_quantity >= ?;
```

Service kiểm tra số dòng cập nhật; tạo order không trừ lần hai. Eligibility loại
items đã có allocation history. Stale/repeated confirmation hoặc serialization/
deadlock conflict cần kiểm tra kết quả và preview mới. Đây là service protocol,
không phải global idempotency-key constraint trong DDL; direct SQL/đường ghi khác
vẫn phải giữ cùng invariants. Không giữ transaction chờ UI hay retry approval cũ.

`remaining_quantity` đã net of committed allocations, không phải tồn kho. Supply
edit khóa row, giữ product/week, không giảm initial dưới commitment. Delete cần
zero committed và không có allocation history. Saved order prices không bị đổi
khi sửa supply price.

## Reviews và scoring

Review submission khóa completed order của đúng shop, lấy supplier từ order và
chặn duplicate trước insert. Comment tối đa 255 Unicode code points. Không có
star rating hay delivery timestamp để đo punctuality độc lập.

History lấy review hợp lệ đầu tiên/order theo `created_at`, `review_id`, đúng
supplier, order hiện COMPLETED và timestamp giữa order creation với cutoff.
Duplicate legacy reviews vẫn được giữ nhưng không nhân trọng số.

Dashboard ranking đã có **40% quality + 30% quantity + 30% on-time** từ tỷ lệ
Boolean feedback. Top tối đa 3 active suppliers, ít nhất 5 eligible reviews;
no reviews thì không tạo score giả. Xem [ranking policy](../docs/supplier-ranking.md).

`supplier_scores` là baseline storage `DECIMAL(5,4)`, không giới hạn 0..1.
Dashboard score 0–100 không được confirmation ghi vào bảng này, và allocation
vẫn ưu tiên giá. Không tự chuẩn hóa/điền bảng hoặc sửa historical scores.
Persisted scoring config/snapshots đầy đủ không được coi là đã triển khai chỉ
vì schema có `algorithm_version` và bảng scores.

## Kiểm chứng

[VALIDATION.md](VALIDATION.md) giữ kết quả DDL lịch sử. Hướng dẫn/kết quả rolling
fixture ở [current-demo.md](../docs/current-demo.md). README gốc có bảng integration
test class/database name/flag. Các fixtures đó tự tạo schema và cần DB mới, trống,
đúng tên; không preload seed hay dùng dữ liệu application thật.

Bản cập nhật tài liệu này đối chiếu source/build/links; không chạy seed, migration
hoặc ghi database. Kết quả test có ngày không được coi là live verification mới
của concurrency hay mọi workflow.
