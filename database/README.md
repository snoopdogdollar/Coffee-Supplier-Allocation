# Database v1 — Coffee Supplier Allocation

Bản SQL này dựa trên ảnh `db_schema.png` của bạn và ba bổ sung đã được chấp thuận trong chat. Đây là bước tạo cấu trúc database; chưa phải toàn bộ nghiệp vụ allocation hoặc ứng dụng Java.

## Các file

- `01_schema.sql`: tạo đúng 13 bảng, primary key, foreign key, unique/check constraints, indexes và comments. Toàn bộ DDL nằm trong một transaction.
- `03_test_queries.sql`: tạo dữ liệu nhỏ để kiểm tra luồng và constraints, sau đó `ROLLBACK`. Không phải seed data sử dụng lâu dài; bộ seed đầy đủ là bước sau. Sequence identity có thể tăng dù dữ liệu rollback, đây là hành vi bình thường.

## So với sơ đồ

Giữ nguyên tên và các cột của 13 bảng. Có những bổ sung/chi tiết triển khai sau:

1. `demands.required_week`, `required_year`: tuần cần hàng, tách khỏi `request_date`.
2. `allocations.run_id`: lần chạy tạo allocation. Composite FK `(run_id, demand_item_id)` bảo đảm allocation thuộc đúng item của run.
3. `allocations.supply_id`: dòng nguồn cung tuần cụ thể được sử dụng.
4. Khóa BIGINT tự sinh bằng `GENERATED ALWAYS AS IDENTITY`. Khi INSERT, bỏ cột ID và dùng `RETURNING ..._id` để lấy giá trị.
5. `order_items.total_price` giữ kiểu `DECIMAL(15,2)` nhưng là generated column: `round(quantity * unit_price, 2)`. Không truyền giá trị cho cột này khi INSERT/UPDATE. Giá trị vượt khả năng kiểu số sẽ bị từ chối.
6. Composite FK bảo đảm supplier của order khớp allocation; supplier của review khớp order. Các unique hỗ trợ composite FK không đổi cardinality trong ảnh.

Không thêm bảng thứ 14. Không thêm inventory, distance hoặc rating sao. Giữ quan hệ **một allocation → nhiều orders**, **một order → nhiều reviews** như ảnh; chưa đặt `UNIQUE(allocation_id)` hoặc `UNIQUE(order_id)` làm thay đổi chúng.

## Quy ước triển khai v1

Đây là lựa chọn triển khai có thể điều chỉnh, không phải tất cả đều được ghi trong ảnh:

- Tuần theo ISO 8601, bắt đầu thứ Hai; `year` là ISO week-numbering year. Một số ngày cuối/tháng đầu năm thuộc ISO year khác năm lịch. SQL kiểm tra đúng số tuần của từng năm, không luôn cho phép tuần 53.
- Mọi item của một demand cần hàng cùng tuần. Nếu cần nhiều tuần, tạo demand riêng; chưa mô hình hóa nhiều tuần trên một item.
- `allocated_price` được hiểu là **đơn giá đã thỏa thuận**, không phải tổng tiền allocation.
- Lượng/giá giữ `DECIMAL(15,2)` theo ảnh. Mọi lượng của một product dùng `coffee_products.unit`; ứng dụng không tự đổi đơn vị. Tất cả giá phải dùng một loại tiền tệ chung do nhóm xác định trước khi seed thật.
- Điểm giữ `DECIMAL(5,4)`, không âm, không NaN. Chưa ép 0..1 và chưa đặt công thức/trọng số. Kiểu này không chứa được score 100; phải thống nhất normalization trước scoring.
- Trạng thái giữ `VARCHAR(20) NOT NULL`, không rỗng; **chưa có enum hoặc state machine** vì chưa chốt vòng đời. `TEST_*` trong test chỉ là dữ liệu kiểm thử, không phải danh sách trạng thái production.
- Contact fields, description, variety, note, comment là optional. Không tự áp unique cho email/phone/name.
- Các cột timestamp giữ `TIMESTAMP` không timezone theo ảnh. Phải dùng một quy ước timezone thống nhất cho các kết nối; đề xuất UTC. Không trộn giờ local từ nhiều máy. `run_time` là cutoff quy ước cho scoring, không tự lọc lịch sử bằng constraint.
- Không cascade delete lịch sử. Foreign key dùng hành vi mặc định `NO ACTION`; có bản ghi tham chiếu thì thao tác xóa bị chặn.
- `remaining_quantity` được truyền rõ khi tạo supply; lần khai báo mới thông thường bằng `initial_quantity`. Không đặt default 0 gây hiểu sai còn hàng.

## Chạy bằng pgAdmin

1. Tạo một database development trống, ví dụ `coffee_supplier_allocation`.
2. Chọn đúng database, mở Query Tool.
3. Mở `01_schema.sql` rồi chạy toàn bộ file.
4. Mở `03_test_queries.sql` rồi chạy toàn bộ file trên database test. Thành công sẽ có thông báo `PASS` và kết thúc bằng `ROLLBACK`.

`01_schema.sql` chỉ chạy một lần trên database trống. Nếu chạy lại sẽ báo bảng đã tồn tại và transaction bị hủy; không có câu lệnh xóa hoặc reset dữ liệu. Nếu Query Tool còn ở failed transaction, chạy `ROLLBACK;` trước thao tác tiếp theo. Thay đổi database có dữ liệu cần migration riêng.

## Chạy bằng PowerShell và psql

Các lệnh dưới là ví dụ: thay đường dẫn PostgreSQL, host, port, username và tên database theo máy. Database cần được tạo trước. Không đưa mật khẩu vào command hoặc commit vào Git.

```powershell
$psqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
& $psqlPath -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_test -v ON_ERROR_STOP=1 -f .\database\01_schema.sql
& $psqlPath -X -h localhost -p 5432 -U postgres -d coffee_supplier_allocation_test -v ON_ERROR_STOP=1 -f .\database\03_test_queries.sql
```

Hai lệnh trên chạy vào database test riêng. Khi tạo database development chính thức, chỉ chạy `01_schema.sql` với tên database development tương ứng. `-X` bỏ qua psql startup config; `ON_ERROR_STOP=1` dừng khi có lỗi. Schema dùng identity/generated stored columns: cần PostgreSQL 12 trở lên; kết quả kiểm thử thực tế ghi trong `VALIDATION.md`.

## Phần database đã bảo vệ

- ID, tham chiếu tồn tại và các composite FK nêu trên.
- Supplier-product không trùng; supply không trùng supplier-product-tuần; score không trùng supplier trong một run.
- Nhu cầu/allocation/order quantity dương, giá không âm; remaining nằm trong khoảng 0..initial.
- Tuần/năm hợp lệ; score không âm; rank dương; các giá trị bắt buộc không NULL.
- Tổng tiền order item luôn được tính từ quantity và unit_price.

## Transaction allocation: phần bắt buộc khi viết JDBC Service

**Schema hiện tại chưa tự thực hiện allocation và chưa chứng minh an toàn concurrency của ứng dụng.** Một `CHECK` không bảo vệ được tổng nhiều dòng hoặc các bảng khác. `supply_id` là FK tới nguồn cung tồn tại, nhưng chưa tự kiểm tra supplier/product/tuần phải khớp demand.

Khi implement service, dùng cùng JDBC Connection và một transaction:

1. Tắt auto-commit; dùng `READ COMMITTED` với giao thức khóa bên dưới, hoặc chiến lược isolation khác đã kiểm thử. Không chờ người dùng trong transaction.
2. Khóa demand headers/items liên quan, rồi supply rows, theo ID tăng dần trong từng nhóm để các đường ghi dùng cùng thứ tự. Nếu xác nhận batch, lấy đủ khóa trước khi ghi.
3. Sau khi lấy khóa, đọc lại trạng thái và nguồn cung bằng câu query mới. Kiểm tra active flags, supplier/product/tuần khớp, run/item khớp và tổng lượng đã cam kết của item. Chốt tập trạng thái được tính là cam kết trước khi viết query này.
4. Dùng cập nhật có điều kiện; số dòng cập nhật phải đúng 1:

```sql
-- JDBC PreparedStatement: bind quantity, supply_id, quantity theo thứ tự.
UPDATE supplier_weekly_supply
SET remaining_quantity = remaining_quantity - ?
WHERE supply_id = ? AND remaining_quantity >= ?;
```

5. Ghi allocation, order và order items trong transaction đó. Không trừ supply thêm lần nữa khi tạo order. Tổng quantities qua các orders của cùng allocation không vượt allocation.
6. Bổ sung khóa idempotency/confirmation theo thiết kế cuối cùng và constraint tương ứng trước khi bật thao tác xác nhận thật. Schema hiện tại **chưa chống double-click/retry tạo allocation/order trùng**; không dùng unique theo run/supplier một cách tùy ý vì có thể chặn nghiệp vụ chia đơn hợp lệ.
7. Commit khi mọi bước thành công; lỗi bất kỳ thì rollback toàn bộ. Deadlock/serialization failure chỉ retry có giới hạn sau rollback và khi thao tác đã idempotent.

Mọi đường sửa/hủy supply, demand, allocation hoặc orders phải theo cùng giao thức. Giảm initial supply không được thấp hơn lượng đã cam kết; hủy chỉ hoàn lượng đúng một lần; sửa supplier-product/tuần của nguồn cung đã có lịch sử cần bị cấm hoặc được xử lý bằng nghiệp vụ rõ ràng.

Các kiểm tra service còn lại: shop của order phải là chủ demand; product của order item phải là product của allocation; supplier của allocation phải là supplier của supply; review chỉ từ shop của order đủ điều kiện hoàn tất. Review có thể nhiều dòng theo ảnh, nên phải chốt semantics lần đánh giá/sửa đánh giá trước khi tổng hợp để không nhân trọng số vô ý.

Scores lưu thành phần và kết quả lịch sử. Để tái hiện đầy đủ còn cần version/config trọng số và snapshot đầu vào; schema ảnh hiện chỉ có `algorithm_version`, chưa đủ audit hoàn chỉnh. Không âm thầm sửa score cũ khi có review mới.

## Bước tiếp theo

Sau khi tạo schema thành công: thống nhất trạng thái, currency, quy tắc review và confirmation/idempotency; sau đó viết `02_seed_data.sql` và Java DAO/Service. Chưa cần UI hoặc thuật toán scoring đầy đủ ở bước database DDL này.
