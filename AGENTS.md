# AGENTS.md — Java Coffee Supplier Allocation

> Hướng dẫn cho coding agent và thành viên phát triển project.
> Đặt file này tại thư mục gốc của repository triển khai.
> Baseline yêu cầu: 12/10/2026. Đây là tài liệu định hướng, không phải bằng chứng rằng code hoặc database đã hoàn thành.

## 1. Mission và MVP

Xây dựng ứng dụng Java giúp coffee shop chọn một hoặc nhiều supplier và phân bổ nguồn cung cà phê theo tuần để đáp ứng nhu cầu mua hàng. Kết quả cần giải thích được, không cấp phát vượt nguồn cung và có thể kiểm chứng bằng dữ liệu lịch sử.

Luồng MVP đầy đủ:

```text
Shop tạo Demand và Demand Items
  → tìm supplier có sản phẩm và nguồn cung trong tuần phù hợp
  → tính score từ các tiêu chí đã chốt và lịch sử có trước thời điểm chạy
  → chọn supplier và phân bổ nguồn cung dùng chung giữa các demand
  → xác nhận allocation và tạo order
  → ghi nhận giao dịch hoàn tất
  → shop review supplier
  → review hợp lệ ảnh hưởng những lần tính score tiếp theo
```

Một demand có thể nhận hàng từ nhiều supplier. Một supplier có thể phục vụ nhiều demand, nhưng mọi demand cạnh tranh trên cùng nguồn cung khả dụng của supplier đó trong cùng tuần và sản phẩm.

Vertical slice đầu tiên: `Create Demand → Read Weekly Supply → Allocate → Display Result`. Hoàn thành phần này trước khi mở rộng UI và toàn bộ vòng đời review.

## 2. Quyết định đã chốt

- Ngôn ngữ chính: **Java**.
- Kiến trúc: **MVC và 3 tier logic**; không mặc định phải triển khai ba máy hoặc ba service.
- Database: **PostgreSQL**.
- Truy cập database: **JDBC và raw SQL**, thông qua DAO.
- **Không dùng ORM** như Hibernate, JPA hoặc cơ chế tự sinh persistence thay JDBC.
- Desktop UI dùng **JavaFX hoặc Swing**; chưa có bằng chứng đã chọn một trong hai.
- Nguồn cung quản lý **theo tuần**, theo supplier và sản phẩm.
- **Không xây dựng quản lý tồn kho**: không có warehouse, stock ledger, nhập/xuất kho hoặc chuyển tồn sang tuần sau trong MVP.
- **Không dùng delivery distance** để lọc supplier, tính score hoặc tối ưu allocation; không xây dựng routing/geolocation.
- Phải xử lý nguồn cung dùng chung giữa các demand và an toàn transaction/concurrency.
- Phải hỗ trợ scoring và review lịch sử.
- Baseline gồm đúng **13 bảng** trong mục 4; không tự ý thêm, bỏ hoặc đổi vai trò bảng.

Nguồn cung theo tuần là cam kết cung ứng phục vụ allocation, không đồng nghĩa với tồn kho vật lý. Địa chỉ liên hệ, nếu có, không mặc nhiên trở thành tiêu chí khoảng cách.

## 3. Phạm vi và thứ tự triển khai

1. Đóng băng yêu cầu MVP và schema v1: cột, PK/FK, unique, check, trạng thái và cardinality.
2. Tạo PostgreSQL schema cùng seed data và các truy vấn kiểm chứng.
3. Tạo kết nối JDBC, domain model và DAO.
4. Triển khai tạo demand, đọc nguồn cung và vertical slice allocation.
5. Hoàn thiện scoring, cạnh tranh giữa demand, transaction xác nhận và orders.
6. Hoàn thiện giao dịch, reviews và historical performance.
7. Hoàn thiện desktop UI, integration tests và demo.

Giữ phân lớp ngay từ vertical slice đầu tiên. Có thể dùng UI tối giản hoặc test harness để chứng minh nghiệp vụ trước khi làm giao diện đầy đủ.

Chưa thuộc MVP: thanh toán thật, bản đồ, giao vận, ERP, microservices, web/mobile app, dashboard nâng cao và hệ thống authentication phức tạp. Không mở rộng các phần này nếu chưa có yêu cầu riêng.

## 4. Baseline database: 13 bảng

**Đã chốt danh sách và vai trò cấp cao; chưa có DDL được xác minh trong ngữ cảnh này.** Các liên kết dưới đây là định hướng để hoàn thiện schema, không phải tuyên bố rằng tên cột, kiểu khóa và cardinality vật lý đã được phê duyệt.

| Bảng | Trách nhiệm | Liên kết nghiệp vụ cần biểu diễn |
|---|---|---|
| `COFFEE_SHOPS` | Coffee shop có nhu cầu mua | Chủ sở hữu demand; bên mua order và người review |
| `SUPPLIERS` | Nhà cung cấp | Cung cấp sản phẩm, khai báo nguồn cung và nhận review |
| `COFFEE_PRODUCTS` | Danh mục sản phẩm cà phê | Sản phẩm được cung cấp và yêu cầu trong demand |
| `SUPPLIER_PRODUCTS` | Sản phẩm mà từng supplier cung cấp | Liên kết supplier với product; thuộc tính chào bán nếu được chốt |
| `SUPPLIER_WEEKLY_SUPPLY` | Nguồn cung theo tuần của một supplier-product | Gắn supplier-product với tuần và lượng cung |
| `DEMANDS` | Yêu cầu mua của một shop | Chủ sở hữu, tuần cần hàng và vòng đời demand |
| `DEMAND_ITEMS` | Các dòng sản phẩm và lượng cần mua | Thuộc demand; tham chiếu product |
| `RECOMMENDATION_RUNS` | Một lần chạy recommendation/allocation | Phạm vi demand, thời điểm, cấu hình và trạng thái chạy |
| `SUPPLIER_SCORES` | Kết quả chấm supplier tại một lần chạy | Gắn run với supplier; lưu score và ngữ cảnh tiêu chí |
| `ALLOCATIONS` | Lượng phân bổ từ nguồn cung tới demand item | Truy vết run, demand item và nguồn cung tuần cụ thể |
| `ORDERS` | Đơn đặt hàng sinh từ allocation được xác nhận | Shop, supplier và trạng thái giao dịch |
| `ORDER_ITEMS` | Dòng hàng đặt mua | Thuộc order; liên hệ allocation và sản phẩm |
| `SUPPLIER_REVIEWS` | Đánh giá supplier sau giao dịch | Người mua, supplier và giao dịch làm căn cứ review |

Nguyên tắc schema:

- Mỗi bảng có primary key rõ ràng; quan hệ dùng foreign key và hành vi xóa được xác định.
- Dùng unique constraint cho các định danh nghiệp vụ cần duy nhất, đặc biệt một dòng nguồn cung cho một supplier-product-tuần theo mô hình đã chốt.
- Lượng demand và allocation phải dương; nguồn cung và giá, nếu có, không âm. Allocation bằng 0 không tạo dòng giả.
- Chọn đơn vị lượng thống nhất và độ chính xác trước khi viết DDL; không trộn kg với g mà không chuyển đổi.
- Dùng PostgreSQL `NUMERIC` và Java `BigDecimal` cho tiền và lượng cần độ chính xác thập phân; tránh `float`/`double` cho các phép tính đó.
- Ràng buộc bằng database những điều database có thể bảo vệ; service vẫn phải kiểm tra nghiệp vụ và trả lỗi dễ hiểu.
- Không trông chờ `CHECK` trên một dòng để bảo vệ tổng allocation của nhiều dòng; cần transaction và cơ chế đồng bộ phù hợp.
- Không cascade delete lịch sử run, allocation, order hoặc review chỉ để thao tác xóa dễ hơn.
- Không tự thêm bảng inventory, distance, user, payment hoặc bảng nối thứ 14. Nếu phát hiện baseline không biểu diễn được nghiệp vụ, trình bày ví dụ, phương án và tác động trước khi sửa thiết kế.

## 5. Quy tắc nghiệp vụ

### 5.1. Weekly supply

- Chỉ chọn supplier cung cấp đúng sản phẩm và có nguồn cung hợp lệ cho tuần cần hàng.
- Cần chốt cách định danh tuần, ngày bắt đầu tuần và timezone; không chỉ dùng số tuần mà thiếu năm.
- Không lấy nguồn cung tuần khác bù tự động và không chuyển phần dư sang tuần sau.
- Trong cùng supplier-product-tuần: `available = declared supply − active committed quantity`.
- Chỉ có một cơ chế kế toán nguồn cung có thẩm quyền. Nếu order được tạo từ allocation đã giữ nguồn cung, không trừ lần thứ hai khi tạo order.
- Không cho giảm declared supply xuống dưới lượng đã cam kết còn hiệu lực.

### 5.2. Allocation và demand cạnh tranh

- Tổng lượng cấp cho một demand item không vượt lượng yêu cầu còn chưa được đáp ứng.
- Tổng cam kết còn hiệu lực trên một dòng weekly supply không vượt declared supply.
- Sau mỗi allocation trong cùng một lần tính, lượng khả dụng dùng cho các demand tiếp theo phải giảm tương ứng.
- Không chạy từng demand với bản sao nguồn cung đầy đủ rồi ghép kết quả lại.
- Score dùng để xếp hạng supplier; score cao không tạo thêm nguồn cung và không thay thế kiểm tra eligibility.
- Thứ tự xử lý demand và tie-break phải rõ ràng, có thể lặp lại khi input/config giống nhau. Không phụ thuộc thứ tự ngẫu nhiên từ SQL hoặc collection.
- Khi thiếu nguồn cung, ghi nhận lượng thiếu và lý do. Chính sách partial fulfillment hay all-or-nothing cần chốt; không tự báo demand hoàn tất.
- Không tuyên bố thuật toán greedy cho kết quả tối ưu toàn cục. Phân biệt supplier ranking, chính sách ưu tiên demand và mục tiêu tối ưu.

### 5.3. Recommendation, confirmation và orders

- Đề xuất chưa xác nhận không tự chiếm nguồn cung, trừ khi sau này có chính sách reservation được phê duyệt.
- Khi xác nhận phải đọc lại nguồn cung và trạng thái demand, vì kết quả preview có thể đã cũ.
- Xác nhận allocation, cập nhật cam kết nguồn cung và tạo order phải thành công hoặc rollback cùng nhau.
- Mỗi order phải xác định rõ bên mua và supplier; quy tắc gộp/tách order từ nhiều allocation cần được chốt trước khi implement.
- Chống xác nhận hai lần: thao tác retry hoặc double-click không tạo allocation/order trùng và không tiêu thụ nguồn cung lần nữa.
- Một run thất bại không được hiển thị như đã tạo order thành công.
- Hủy phải chuyển trạng thái hợp lệ và giải phóng đúng lượng cam kết, đúng một lần. Không tự cho phép hủy giao dịch đã hoàn tất.
- Lưu đủ dữ liệu giao dịch tại thời điểm đặt để thay đổi thông tin sản phẩm hoặc giá sau này không làm sai lịch sử order.

### 5.4. Scoring và historical reviews

- Lọc điều kiện bắt buộc trước, sau đó mới chấm điểm các supplier đủ điều kiện.
- Công thức, trọng số, hướng tốt/xấu của tiêu chí và normalization phải được ghi thành tài liệu/config rõ ràng.
- Không tự coi giá, capacity, rating hoặc reliability là tiêu chí đã chốt; đó là các ứng viên cần quyết định.
- Xử lý supplier chưa có review bằng chính sách công khai; không tự gán score tốt nhất hoặc thấp nhất.
- Review chỉ hợp lệ khi người review là shop của giao dịch đủ điều kiện, cho đúng supplier và sau khi giao dịch hoàn tất theo trạng thái đã chốt.
- Chốt rating scale và quy tắc chống review trùng; không lấy review không hợp lệ hoặc giao dịch bị loại vào aggregate.
- Chỉ dùng dữ liệu tồn tại trước hoặc tại cutoff của run. Review mới ảnh hưởng run tương lai, không viết lại score lịch sử.
- Lưu thời điểm cutoff, phiên bản/config scoring, thành phần điểm và dữ liệu tóm tắt đủ để giải thích kết quả. Giá trị hiện tại của supplier không thay thế snapshot lịch sử.
- Phân biệt rating do shop đánh giá với performance đo từ order. Không tính tỷ lệ đúng hạn nếu chưa có định nghĩa và dữ liệu thời gian cần thiết.
- Định nghĩa rõ score ở mức supplier chung hay theo product/demand item; không dùng một score cho nhiều ngữ cảnh khác nhau nếu công thức phụ thuộc ngữ cảnh đó.

## 6. Transaction và concurrency safety

Service sở hữu ranh giới transaction. Các DAO tham gia cùng một nghiệp vụ xác nhận phải dùng **cùng JDBC Connection**; DAO không tự mở connection khác hoặc tự commit giữa chừng.

Luồng xác nhận cần bảo đảm:

1. Bắt đầu transaction, tắt auto-commit trong phạm vi thao tác.
2. Kiểm tra/chặn xác nhận lặp lại theo khóa nghiệp vụ được bảo vệ bằng database.
3. Khóa hoặc kiểm soát phiên bản các demand item và weekly supply liên quan theo thứ tự ổn định.
4. Đọc lại trạng thái, lượng còn cần và lượng còn khả dụng bên trong transaction.
5. Kiểm tra các bất biến, ghi allocation, cam kết nguồn cung, orders và order items.
6. Đánh dấu kết quả xác nhận phù hợp rồi commit. Có lỗi thì rollback toàn bộ; giải phóng tài nguyên và phục hồi cấu hình connection nếu trả về pool.

Lựa chọn kỹ thuật phải được ghi lại: ví dụ PostgreSQL row lock với `SELECT ... FOR UPDATE`, hoặc optimistic locking/conditional update có kiểm tra số dòng cập nhật; dùng isolation level phù hợp. Không cần triển khai mọi chiến lược cùng lúc.

Mọi đường ghi nguồn cung hoặc allocation phải tuân theo cùng giao thức đồng bộ. Khóa weekly supply không đủ nếu một đường ghi khác bỏ qua khóa đó. Cũng cần bảo vệ demand item để hai giao dịch không cùng cấp vượt nhu cầu từ hai nguồn cung khác nhau.

Nếu gặp deadlock/serialization conflict, rollback và chỉ retry có giới hạn toàn bộ transaction khi thao tác bảo đảm idempotency. Không nuốt lỗi và tiếp tục trên transaction đã hỏng. Không giữ transaction mở trong lúc chờ người dùng bấm xác nhận.

## 7. MVC và 3 tier

```text
Presentation: JavaFX/Swing View → Controller
                                      ↓
Business:                 Service → Matching/Scoring/Allocation
                                      ↓
Data access:                    DAO → JDBC → PostgreSQL
```

- **View:** hiển thị dữ liệu, thu thập input và thông báo lỗi; không chứa SQL hoặc thuật toán allocation.
- **Controller:** chuyển input thành yêu cầu cho service, xử lý luồng UI; không chứa quy tắc phân bổ hoặc quản lý transaction.
- **Model/domain:** biểu diễn thực thể, value object và dữ liệu nghiệp vụ. MVC Model không đồng nghĩa với riêng các bảng database.
- **Service:** validation nghiệp vụ, use case, transaction và điều phối DAO/algorithm.
- **Algorithm:** eligibility, scoring và allocation có thể kiểm thử độc lập; ưu tiên nhận dữ liệu đầu vào rõ ràng thay vì tự truy cập database.
- **DAO:** raw SQL, `PreparedStatement`, mapping result và thao tác persistence; không quyết định ưu tiên demand hoặc trọng số score.
- **Connection/config:** đọc cấu hình ngoài source code; không hard-code mật khẩu.

Không chạy JDBC hoặc tác vụ tính dài trên JavaFX Application Thread/Swing EDT. Cập nhật UI trên thread UI phù hợp và chặn double-click chỉ như hỗ trợ trải nghiệm; database/service vẫn phải chống ghi trùng.

Cấu trúc tham khảo, chỉ áp dụng khi repository chưa có quy ước tương đương:

```text
database/
  01_schema.sql
  02_seed_data.sql
  03_test_queries.sql
src/main/java/<base-package>/
  model/
  view/
  controller/
  service/
  algorithm/
  dao/
  config/
src/main/resources/
src/test/java/<base-package>/
docs/
  schema-v1.md
  business-rules.md
  scoring.md
  decisions.md
README.md
AGENTS.md
```

Các đường dẫn này là đề xuất, không phải danh sách file đã tồn tại. Không tái cấu trúc repository đang có chỉ để khớp ví dụ.

## 8. Commands và cấu hình — placeholders

**Các dòng dưới chưa phải lệnh chạy được.** Thay nội dung trong `<...>` sau khi kiểm tra repository và công cụ đã chọn. Không báo build/test pass dựa trên các placeholder này.

| Tác vụ | Placeholder |
|---|---|
| Kiểm tra Java | `<JAVA_VERSION_COMMAND>` |
| Cấu hình database local | `<LOCAL_DB_CONFIG_STEPS>` |
| Tạo schema trên database dev rỗng | `<APPLY_SCHEMA_COMMAND>` |
| Nạp seed data | `<LOAD_SEED_COMMAND>` |
| Chạy truy vấn kiểm chứng | `<VERIFY_DATABASE_COMMAND>` |
| Build | `<BUILD_COMMAND>` |
| Unit tests | `<UNIT_TEST_COMMAND>` |
| Integration tests trên PostgreSQL test | `<INTEGRATION_TEST_COMMAND>` |
| Chạy desktop app | `<RUN_APP_COMMAND>` |

Chưa chốt JDK version, Maven/Gradle, main class, JavaFX module configuration hoặc PostgreSQL version. Kiểm tra build file/wrapper hiện có trước khi điền lệnh.

Tên cấu hình gợi ý: `DB_URL`, `DB_USER`, `DB_PASSWORD`. Xác minh cơ chế nạp thực tế; Java không tự đọc file `.env` nếu chưa có hỗ trợ. Chỉ commit file cấu hình mẫu không chứa secret.

Seed/reset chỉ chạy vào database development hoặc test đã xác định rõ. Không ghi đè dữ liệu thật, in mật khẩu vào log hoặc tự chạy thao tác phá hủy database.

## 9. Quy tắc làm việc cho agent

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

## 10. Testing và acceptance criteria

### Unit tests

- Eligibility đúng product, đúng tuần và nguồn cung phù hợp.
- Scoring: normalization, trọng số, dữ liệu thiếu, supplier mới, tie-break và cutoff lịch sử.
- Allocation: một/nhiều supplier, một/nhiều demand cạnh tranh, vừa đủ/thiếu/không có supply, lượng thập phân và lượng cần còn lại.
- Cùng input và config cho kết quả ổn định; tổng lượng luôn thỏa các bất biến.
- UI/controller không phải nơi duy nhất kiểm tra điều kiện nghiệp vụ.

### PostgreSQL integration tests

- PK/FK/unique/check hoạt động; DAO mapping và truy vấn đúng.
- Lỗi giữa lúc ghi order và allocation rollback toàn bộ phần xác nhận.
- Gửi lại cùng confirmation không sinh order trùng hoặc trừ supply hai lần.
- Hai connection thật xác nhận đồng thời, có điểm đồng bộ để tạo tranh chấp trên cùng nguồn cung: tổng commit không vượt capacity.
- Hai connection cấp phát cùng demand item từ các supplier khác nhau: tổng commit không vượt nhu cầu.
- Preview cũ được revalidate; cập nhật supply và cancellation không phá bất biến.
- Review không đúng shop/supplier, giao dịch chưa đủ điều kiện và review trùng bị xử lý theo quy tắc đã chốt.
- Review mới chỉ thay đổi kết quả run mới; run lịch sử vẫn giữ nguyên giải thích và score.

Không dùng database giả/in-memory làm bằng chứng duy nhất cho PostgreSQL locking và concurrency. Tests phải có database riêng, dữ liệu cô lập và cleanup an toàn.

### Demo end-to-end

Seed đề xuất: khoảng **5 shops, 8–10 suppliers, 5 coffee products**, nhiều tuần supply và giao dịch/review lịch sử giả lập hợp lệ.

Demo phải cho thấy: tạo demand → supplier đủ điều kiện → score có giải thích → chia allocation → xác nhận → xem order → ghi nhận hoàn tất → review → chạy lại recommendation và thấy ảnh hưởng hợp lý của lịch sử mới.

Bổ sung tình huống nhiều shop tranh nguồn cung và một demand không được đáp ứng đủ. UI phải thể hiện lượng cấp, lượng thiếu và lý do; không chỉ hiển thị tên supplier thắng.

Một tính năng hoàn thành khi: đúng phạm vi, giữ các bất biến, có kiểm chứng phù hợp đã chạy hoặc nêu rõ lý do chưa chạy, tài liệu nhất quán và không còn placeholder nghiệp vụ âm thầm chi phối kết quả.

## 11. Deliverables dự kiến

- Schema v1 cho 13 bảng, mô tả PK/FK/cardinality/constraints và ERD tương ứng.
- SQL tạo database schema, seed data và truy vấn kiểm chứng; migration khi schema đã có dữ liệu cần thay đổi.
- Java source theo MVC/3 tier, JDBC DAO và cấu hình mẫu.
- Tài liệu scoring, demand priority, allocation, trạng thái và transaction strategy.
- Desktop UI tối thiểu phục vụ luồng MVP.
- Unit tests, PostgreSQL integration tests và kịch bản concurrency.
- README có prerequisites và lệnh thực tế đã xác minh, cùng kịch bản demo và giới hạn đã biết.

Danh sách này là mục tiêu bàn giao của project, không phải tuyên bố các artifact đã tồn tại.

## 12. Decisions, assumptions và câu hỏi còn mở

| Hạng mục | Trạng thái | Hành động trước khi implement phần phụ thuộc |
|---|---|---|
| Mission, MVP, Java, MVC/3 tier, PostgreSQL/JDBC/raw SQL/no ORM | Đã chốt | Tuân theo mục 1–2 |
| Baseline 13 bảng | Đã chốt danh sách | Hoàn thiện DDL; không coi tên cột/khóa là đã xác nhận |
| Không inventory và delivery distance; weekly supply | Đã chốt | Giữ đúng phạm vi |
| JavaFX hay Swing | Chưa chốt | Kiểm tra lựa chọn có sẵn hoặc thống nhất một UI toolkit |
| JDK, PostgreSQL version, Maven hay Gradle | Chưa chốt | Kiểm tra môi trường/build file rồi ghi lựa chọn |
| Đơn vị quantity, độ chính xác, tiền tệ và price scope | Chưa chốt | Thống nhất trước schema/thuật toán có liên quan |
| Tuần, timezone và thời điểm khóa supply | Chưa chốt | Định nghĩa ranh giới thời gian nhất quán |
| Công thức score, trọng số, normalization, cold start | Chưa chốt | Viết ví dụ tính điểm và expected results |
| Phạm vi run: một demand hay batch nhiều demand | Chưa chốt | Bảo đảm truy vết phạm vi trong baseline 13 bảng |
| Grain của `SUPPLIER_SCORES` | Chưa chốt | Chọn supplier/run hay ngữ cảnh item phù hợp công thức |
| Ưu tiên demand, fairness, tie-break và mục tiêu tối ưu | Chưa chốt | So sánh trên kịch bản nguồn cung thiếu |
| Partial fulfillment hay all-or-nothing | Chưa chốt | Định nghĩa kết quả và trạng thái khi thiếu hàng |
| Trạng thái, hủy/sửa demand và order grouping | Chưa chốt | Viết state transitions và điều kiện hợp lệ |
| Review scale, quyền sửa và chống trùng | Chưa chốt | Gắn với giao dịch và cutoff lịch sử |
| Locking/isolation/idempotency và retry strategy | Yêu cầu an toàn đã chốt; cơ chế chưa chốt | Chọn, ghi lý do và kiểm thử cạnh tranh thực tế |
| Số lượng seed, cấu trúc thư mục và tên config | Đề xuất | Điều chỉnh theo repository và nhu cầu demo |

Ghi quyết định mới trong `docs/decisions.md` hoặc tài liệu tương đương đang có: ngày, vấn đề, lựa chọn, lý do, ảnh hưởng schema/code/tests. Khi quyết định thay đổi, cập nhật cả tài liệu liên quan thay vì để code và hướng dẫn mâu thuẫn.

**Công việc đầu tiên:** kiểm tra tài liệu/schema hiện có; hoàn thiện `database/01_schema.sql` cho baseline 13 bảng cùng các quyết định cần thiết, sau đó seed và kiểm chứng truy vấn tìm supplier đủ điều kiện. Không bắt đầu bằng UI đầy đủ hoặc công thức score chưa thống nhất.
