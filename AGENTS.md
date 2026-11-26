# AGENTS.md — Java Coffee Supplier Allocation

Hướng dẫn cho coding agent và thành viên phát triển. Cập nhật theo source ngày
**26/11/2026**. Mô tả implementation không thay cho kiểm tra source/cấu hình hoặc
bằng chứng chạy thực tế trong mỗi nhiệm vụ.

## 1. Phạm vi và nguồn đối chiếu

- Checkout chạy ứng dụng trên máy hiện tại: `C:\Users\MY PC\Desktop\java`.
  Trên máy khác, dùng repository root chứa `pom.xml` và `src/`.
- Khi làm ở mirror ChatGPT, `sources/` là tài liệu chỉ đọc: không sửa, đổi tên,
  di chuyển hoặc xóa file đồng bộ. Phân biệt staged work với checkout chạy thật.
- Đọc [README.md](README.md), build config, schema và source liên quan trước khi
  sửa. Tài liệu tính năng ở `docs/`; quyết định ở [decisions.md](docs/decisions.md).
- Source/build/SQL là căn cứ hành vi hiện tại. Báo cáo verification có ngày chỉ
  chứng minh lần chạy đó; không trình bày như kết quả vừa chạy.

## 2. Sản phẩm và công nghệ hiện có

Ứng dụng desktop giúp coffee shop gửi demand và phân bổ nguồn cung dùng chung
theo tuần từ một hoặc nhiều supplier. Luồng PostgreSQL hiện có:

```text
Supplier khai báo sản phẩm/nguồn cung tuần
  → Buyer tạo demand nhiều dòng
  → Admin preview và xác nhận allocation, tạo orders
  → Buyer xác nhận nhận hàng, review supplier
  → Admin xem lịch sử và dashboard ranking từ review hợp lệ
```

| Thành phần | Implementation |
| --- | --- |
| Java/build | JDK 26, Maven, compiler release 26; không có Maven wrapper. |
| JavaFX | Controls/FXML 21.0.6; `com.coffeemongers.App`. |
| Swing | `com.coffeeallocation.DesktopMain`; vẫn được giữ lại. |
| Console | `com.coffeeallocation.Main`; menu 0–13, chọn tuần bằng menu 13. |
| Persistence | PostgreSQL, pgJDBC 42.7.14, JDBC/raw SQL qua DAO; không ORM. |
| Node.js | 22.20+ cho environment adapter và launcher/npm tooling; không có Node backend server. |
| Tests | JUnit trong `src/test/java/`; Node tests trong `scripts/`. |

JavaFX có Buyer/Supplier/Admin, demo services và PostgreSQL adapters cho test
identities. Swing nhập shop ID thủ công; các interface có phạm vi khác nhau.
Không tự xóa Swing/console khi sửa JavaFX.

## 3. Lệnh chạy và cấu hình

Chạy từ repository root:

| Mục đích | Lệnh PowerShell |
| --- | --- |
| Kiểm tra công cụ | `java -version`, `javac -version`, `mvn -version` |
| Kiểm tra JavaFX settings | `.\run-javafx.ps1 -Check` |
| JavaFX có cấu hình DB | `.\run-javafx.ps1` hoặc `npm.cmd run javafx` |
| JavaFX demo | `.\run-javafx.ps1 -Demo` |
| JavaFX qua Maven trực tiếp | `mvn javafx:run` |
| Console | `npm.cmd run build`, sau đó `npm.cmd start` |
| Swing | `npm.cmd run desktop`; hoặc `.\run-desktop.ps1` khi đã đặt JDBC variables |
| Build | `mvn compile` / `mvn package` |
| Java tests | `mvn test` |
| Node config/launcher tests | `npm.cmd test` |

Launcher JavaFX kiểm tra JDK/Maven/Node, nạp settings rồi chạy
`mvn compile javafx:run`. Backend Java chạy cùng ứng dụng. Database phải có sẵn;
launcher không khởi động PostgreSQL, provision Neon, tạo schema hoặc seed.
`-Check` không kiểm tra database connection, credentials, schema hay record IDs.

- Java/JDBC đọc `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- JavaFX/npm launchers dùng `scripts/database-env.mjs`: process `DATABASE_URL` →
  process JDBC triple → `.env` nếu tồn tại, nếu không `.env.local`. Process database
  configuration được chọn như một bộ hoàn chỉnh; khi đó file loader không dùng.
- Maven/Java trực tiếp không tự đọc dotenv hoặc chuyển `DATABASE_URL` sang JDBC.
- Buyer thử: `COFFEE_TEST_SHOP_ID` và `COFFEE_TEST_BUYER_PASSWORD`.
- Supplier thử: `COFFEE_TEST_SUPPLIER_ID` và `COFFEE_TEST_SUPPLIER_PASSWORD`.
- Admin thử: `COFFEE_TEST_ADMIN_PASSWORD`.
- Login emails: `buyer@coffeemongers.test`, `supplier@coffeemongers.test`,
  `admin@coffeemongers.test`. Mật khẩu do người chạy đặt: ít nhất 10 ký tự, có
  chữ hoa, chữ thường, số và ký tự đặc biệt. IDs phải hợp lệ; nghiệp vụ tiếp tục
  kiểm tra active flags và ownership. Đổi account settings cần restart.
- Business record không phải login account. Public registration và demo accounts
  dùng bộ nhớ, chưa phải production authentication. Admin workspace preview của
  Buyer/Supplier là chỉ đọc, không đổi thành test identity của các role đó.
- `-Demo` bỏ database/test-account settings chỉ trong child process và không đọc
  dotenv. Giữ nguyên file cấu hình và môi trường gốc của người dùng.

Chi tiết: [JavaFX launcher](docs/javafx-launcher.md). Không đưa secrets vào source,
command arguments, logs, tài liệu hoặc commit. Chỉ commit cấu hình mẫu không secret;
giữ `.env`/`.env.local` và `.neon` ngoài Git.

## 4. Database và demo fixtures

[database/01_schema.sql](database/01_schema.sql) là DDL hiện có với đúng **13 bảng**,
identity PK, FK/composite FK, unique/check constraints, indexes và generated totals:

`coffee_shops`, `suppliers`, `coffee_products`, `supplier_products`,
`supplier_weekly_supply`, `demands`, `demand_items`, `recommendation_runs`,
`supplier_scores`, `allocations`, `orders`, `order_items`, `supplier_reviews`.

- Chạy schema một lần trên DB development/test trống; DB có dữ liệu cần migration
  riêng được xem xét, không chạy lại DDL để reset.
- Chọn **một seed**: `02_seed_data.sql` là lịch sử tuần 41–43/2026;
  `04_current_demo_data.sql` tính tuần hiện tại/tuần sau từ database clock theo
  `Asia/Ho_Chi_Minh`. Cả hai kiểm tra tables trống dưới lock và từ chối nếu có data.
- `03_test_queries.sql` kiểm chứng DDL bằng fixture có `ROLLBACK`, không phải seed
  lâu dài. Sequences có thể tăng sau rollback; dùng IDs thực tế/`RETURNING`.
- Seed không tự dịch tuần đã lưu. Dùng DB demo mới hoặc thêm supply/demand hiện
  tại qua app; không viết lại lịch sử thật. Xem [database guide](database/README.md)
  và [rolling demo](docs/current-demo.md).
- Giữ baseline 13 bảng. Không tự thêm inventory, distance, login, payment hay bảng
  thứ 14. Thay đổi schema có ý nghĩa nghiệp vụ phải nêu lý do, dữ liệu ví dụ, tác
  động PK/FK và migration trước khi thay đổi quyết định đã chốt.

## 5. Business rules phải giữ

### Weekly supply và allocation

- Supply là cam kết supplier-product-ISO year/week, không phải tồn kho vật lý.
  Không carry-over, tự mượn tuần khác, distance hoặc routing.
- Units lấy từ `coffee_products.unit`; không tự đổi kg/g hoặc cộng khác unit.
  Quantity/giá dùng `BigDecimal` / `DECIMAL(15,2)`. Fixture/console dùng VND.
  `allocated_price` là đơn giá snapshot, không phải tổng tiền.
- `remaining_quantity` đã trừ committed allocations; tạo order không trừ lần hai.
- Demand priority: `request_date`, `demand_id`, `demand_item_id` tăng dần.
- Supplier priority: `unit_price`, rồi `supplier_id` tăng dần; kiểm tra active
  shop/product/supplier/offering, cùng sản phẩm/tuần và còn nguồn cung.
- **All-or-nothing từng demand item**: có thể chia nhiều supplier để đủ một dòng.
  Dòng thiếu không tiêu thụ gì, các dòng khác vẫn có thể được cấp. Greedy này
  deterministic, không tuyên bố tối ưu toàn cục.
- Preview dùng chung một balance giữa các dòng, không giữ chỗ/ghi allocation.
  Catch-up expiry có thể ghi trạng thái trong transaction riêng trước phần đọc
  preview; không gọi toàn bộ invocation là chỉ đọc.
- Supply edit giữ product/tuần, initial không thấp hơn commitment. Delete cần
  zero committed và không có allocation history. Thay giá không đổi saved prices.
  Deactivate/stop offering giữ lịch sử; không cascade-delete để thao tác dễ hơn.

### Confirmation, orders và expiry

- `AllocationConfirmationService` dùng `SERIALIZABLE`, khóa demand headers/items
  rồi supply theo thứ tự ổn định, đọc lại và so sánh snapshot với preview đã xem.
  Các DAO tham gia dùng cùng connection/transaction do service sở hữu.
- Run/allocation `CONFIRMED`, supply deduction, orders `PENDING` và demand status
  cùng commit/rollback. Một run gắn một demand item; implementation hiện tạo một
  order/supplier allocation. DDL vẫn cho một allocation có nhiều orders.
- Batch xác nhận **mọi dòng đủ cung của tuần hiển thị**, không chỉ dòng đang chọn.
  Snapshot cũ, confirm lại hoặc serialization/deadlock conflict cần kiểm tra kết
  quả và preview mới; không retry approval cũ hoặc giữ transaction chờ UI.
- Buyer sở hữu order thực hiện receipt: `PENDING` → `COMPLETED`. Supplier Incoming
  Orders chỉ đọc; Admin monitor không hoàn tất order thay Buyer.
- Demand `OPEN` → `ALLOCATED` khi mọi dòng fulfilled; OPEN quá hạn còn thiếu thành
  `EXPIRED`. `CANCELLED` là trạng thái khác, không có general cancellation/refund
  action. Không suy ra chức năng hủy chỉ từ một status column.
- ISO week kết thúc 00:00 thứ Hai kế tiếp theo `Asia/Ho_Chi_Minh`. Backend từ chối
  tạo demand/matching/allocation cho tuần quá hạn. Menu 13 cho xem tuần lịch sử
  không bỏ qua guard. Console chọn tuần hiện tại lúc startup theo business zone.
- Expiry giữ allocations, supply, orders và reviews. JavaFX catch-up 60 giây khi
  workspace đủ điều kiện; foreground access cũng catch-up. Không có job server
  khi mọi app đã tắt. Xem [demand expiry](docs/demand-expiry.md).

### Reviews và dashboard ranking

- Review gồm `quality_ok`, `quantity_ok`, `on_time_ok` Boolean, comment tùy chọn.
  Service khóa completed order của đúng shop và chặn review thứ hai/order.
- DDL giữ nhiều reviews/order cho legacy data. Report lấy review hợp lệ đầu tiên
  theo `created_at`, `review_id`, đúng supplier, giữa order creation và cutoff,
  order hiện COMPLETED. Không nhân trọng số vì duplicate legacy rows.
- Dashboard score = 40% quality rate + 30% quantity rate + 30% on-time rate.
  Top tối đa 3 active suppliers với ít nhất 5 eligible reviews; tie-break score
  giảm, review count giảm, supplier ID tăng. No reviews không tự gán score 0.
- **Allocation vẫn ưu tiên giá**. Confirmation không ghi dashboard score vào
  `supplier_scores`. Không tự đổi thuật toán, chuẩn hóa hoặc invent scores chỉ để
  điền bảng. Xem [ranking policy](docs/supplier-ranking.md).
- DDL dùng `TIMESTAMP` không timezone. Kiểm tra writer convention và JDBC session
  timezone thực tế; không giả định mọi connection UTC. Cutoff/history phản ánh
  dữ liệu hiện có, không đo độc lập giờ giao hoặc tái dựng status history thiếu.

## 6. Kiến trúc và phạm vi

```text
JavaFX/Swing/console view → controller → service/algorithm → DAO → JDBC → PostgreSQL
```

- View hiển thị/thu input; controller điều phối; service validation/transaction;
  algorithm nhận input rõ ràng; DAO bind SQL/map row. Không đặt SQL/thuật toán
  allocation hoặc transaction trong UI controller.
- `com.coffeemongers` có live adapters, demo services và UI; không gọi cả package
  chỉ là frontend. `com.coffeeallocation` có backend, console, Swing. Không tái cấu
  trúc repository chỉ để giống một cây thư mục minh họa.
- Dùng `PreparedStatement`; SQL identifiers phải từ whitelist. Đóng resources,
  giữ nguyên nhân lỗi; không catch và báo fake success.
- Database/tác vụ dài chạy ngoài JavaFX thread/Swing EDT; UI updates trên đúng
  thread. UI double-click guard không thay thế consistency ở service/database.
- Mọi đường ghi supply/allocation phải giữ cùng invariants/locking protocol.
- Admin Supply by Coffee Type poll riêng mỗi 5 giây khi dashboard authenticated
  đang hiển thị. Đây không phải server push hay auto-refresh mọi màn hình; các
  view khác giữ entry/manual refresh trừ khi source có cơ chế riêng.
- Scope MVP không có payment, ERP, web/mobile, routing, warehouse ledger hoặc
  production authentication. Không mở rộng nếu chưa có yêu cầu.

## 7. Kiểm chứng và bàn giao

- Phân biệt source đã có, test vừa chạy, kết quả lịch sử và phần chưa kiểm chứng.
  Không chứng minh concurrency chỉ bằng commit/rollback hay in-memory tests.
- `mvn test` gồm JavaFX tests cần graphical desktop. `npm.cmd test` kiểm tra config/
  launcher, không thay cho Java/JDBC integration.
- Integration tests là opt-in, tự tạo schema và cần DB **mới, trống, disposable**
  đúng tên mỗi fixture. Xem bảng class/database/flag trong README. Không preload
  seed hoặc dùng database thật. `CurrentDemoIntegrationTest` yêu cầu
  `coffee_current_demo_test` và `COFFEE_CURRENT_DEMO_INTEGRATION=true`.
- Kiểm chứng phù hợp thay đổi: happy path, input sai, thiếu supply, snapshot cũ,
  duplicate confirmation/review, rollback và concurrency nếu sửa transaction.
  Không dùng một report cũ để khẳng định đã chạy lại toàn bộ workflow.
- Demo phải show persistence và đúng role. Dashboard review score cập nhật không
  có nghĩa allocation đổi supplier theo điểm đó.
- Khi staged ngoài checkout, đối chiếu baseline và chỉ copy files đã thay đổi.
  Báo rõ thay đổi, kiểm chứng thực tế, giới hạn và work chưa chạy.

## 8. Quy tắc làm việc cho agent

1. Đọc `AGENTS.md`, README, build config, schema, migration và code liên quan trước khi sửa. So sánh tài liệu với trạng thái thực tế; báo khác biệt thay vì đoán.
2. Nếu làm trong bản mirror ChatGPT, coi `sources/` là tài liệu chỉ đọc: không sửa, đổi tên, di chuyển hoặc xóa file đồng bộ.
3. Nêu mục tiêu, file/phạm vi sẽ đổi và cách kiểm chứng trước thay đổi đáng kể. Giữ ranh giới bước khi người dùng yêu cầu làm từng bước.
4. Hoàn thành phần đã được giao; không mở rộng thành viết toàn bộ ứng dụng, đổi framework hoặc thêm dependency không cần thiết.
5. Giữ baseline 13 bảng. Với thay đổi schema có ý nghĩa nghiệp vụ, trình bày lý do, ví dụ dữ liệu, tác động PK/FK và kế hoạch migration trước khi thay đổi quyết định đã chốt.
6. Không tự giải quyết quyết định nghiệp vụ còn mở bằng một giá trị tùy ý. Ghi giả định rõ ràng; xin quyết định khi nó làm thay đổi kết quả allocation, dữ liệu hoặc phạm vi.
7. Dùng JDBC `PreparedStatement` để bind dữ liệu; không ghép input người dùng vào SQL. Với identifier không bind được, dùng tập giá trị cho phép.
8. Đóng JDBC resources đúng cách, giữ nguyên nguyên nhân lỗi, không catch rồi trả kết quả thành công giả.
9. Không làm mất thay đổi của người khác; không reset/clean hoặc xóa dữ liệu để làm test xanh.
10. Cập nhật tài liệu và test phù hợp khi thay đổi hành vi. Giữ code dễ hiểu, ưu tiên correctness và giải thích được hơn abstraction phức tạp.
11. Phân biệt rõ: đã implement, đã chạy test, chưa chạy và giả định. Không tuyên bố an toàn concurrency chỉ vì đã có `commit()`/`rollback()`.
12. Khi bàn giao, nêu thay đổi, kiểm chứng thực tế, giới hạn và bước còn lại. Không ghi secret hoặc dữ liệu nhạy cảm vào bản bàn giao.

