# Phase 8 — Giao diện Java desktop

Giao diện dùng Swing có sẵn trong JDK 26. Điểm vào là `com.coffeeallocation.DesktopMain`; chương trình console `Main` và menu 1–12 vẫn chạy riêng như trước.

## Chạy trên Windows

Mở terminal trong thư mục dự án và chạy:

```powershell
npm run desktop
```

Lệnh này dùng lại bộ nạp cấu hình của console: `DATABASE_URL`, hoặc bộ `DB_URL`/`DB_USER`/`DB_PASSWORD` từ môi trường hoặc `.env`/`.env.local` theo quy tắc hiện có. Không chép thông tin đăng nhập vào source hoặc tài liệu. Các lệnh `npm start`, `npm run build` vẫn giữ nguyên.

Nếu đã đặt ba biến `DB_URL`, `DB_USER`, `DB_PASSWORD` trong PowerShell, có thể gọi launcher trực tiếp:

```powershell
.\run-desktop.ps1
```

Launcher Windows biên dịch source vào `target/desktop-classes` rồi mở giao diện. Cần JDK 26 và pgJDBC đúng phiên bản trong `pom.xml` đã có trong Maven cache (chạy `mvn compile` lần đầu nếu chưa có). Launcher ưu tiên `JAVA_HOME`, sau đó JDK 26 trong `Program Files/Java`, cuối cùng mới dùng Java trong PATH. Có thể chọn đường dẫn JDK rõ ràng:

```powershell
.\run-desktop.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.2.1'
```

## Các màn hình

| Màn hình | Thao tác |
|---|---|
| Nhu cầu | Tải quán/sản phẩm đang hoạt động; tạo demand nhiều dòng với ngày yêu cầu, năm/tuần ISO, lượng thập phân và ghi chú; xem các dòng OPEN theo tuần |
| Phân bổ & nguồn cung | Xem preview dùng chung nguồn cung; chọn dòng để xem supplier, giá, lượng cấp và phần còn lại sau toàn lượt; xác nhận tất cả dòng đủ cung của tuần |
| Đơn hàng | Tải danh sách có phân trang, chọn hoặc nhập mã đơn để xem chi tiết, nhập đúng mã quán để xác nhận đơn PENDING đã giao |
| Đánh giá | Nhập mã quán/đơn; xem review đã lưu; trả lời Có/Không cho cả ba tiêu chí và gửi một review cho đơn COMPLETED |
| Lịch sử supplier | Xem tổng hợp các review hợp lệ tại cutoff, số lượt đạt/tổng và phần trăm; supplier chưa có review hiển thị N/A |

Năm/tuần mặc định lấy theo lịch ISO hiện tại. Bộ seed minh họa cũ dùng tuần **42/2026**; cần nhập đúng tuần đó khi thử trên database seed.

## Kịch bản thử với dữ liệu demo

1. Chọn database thử đã nạp schema và seed. Mở Nhu cầu, chọn 2026/42 rồi tải OPEN để xem mã quán và dòng demand.
2. Tạo demand với một hoặc nhiều dòng sản phẩm. Kiểm tra phần xác nhận; chỉ sau khi service commit thành công mới báo mã demand và xóa nội dung đã gửi. Nếu lỗi, form giữ lại dữ liệu.
3. Mở Phân bổ & nguồn cung, nhập cùng năm/tuần và xem preview. Kiểm tra lượng cần, lượng đề xuất, lượng chưa cấp và thiếu cung. Chọn từng dòng để xem supplier và đơn giá.
4. Nút xác nhận áp dụng cho **toàn bộ dòng đủ cung của tuần**, không chỉ dòng đang chọn. Preview không giữ nguồn cung; service kiểm tra lại đúng snapshot đã hiển thị trước khi ghi. Khi có xung đột, kiểm tra đơn hàng rồi tải preview mới.
5. Mở Đơn hàng, bấm tải và xem chi tiết mã đơn vừa tạo. Nhập Shop ID của đúng quán trong đơn rồi xác nhận đã giao để chuyển sang COMPLETED.
6. Mở Đánh giá, nhập Shop ID và Order ID đó; chọn ba câu trả lời và gửi. Tải lại đánh giá; service chặn gửi lần thứ hai.
7. Mở Lịch sử supplier. Để cutoff trống dùng đồng hồ database, hoặc nhập thời gian local ISO như `2026-11-07T14:30:00`. UI hiển thị mốc và múi giờ do service trả về.

## Quy tắc kỹ thuật và phạm vi

- UI gọi các service hiện có; SQL vẫn ở DAO. `DesktopCatalogService` bổ sung lớp đọc danh mục và các dòng demand OPEN cho UI.
- `UiTasks` chạy dịch vụ trên worker, cập nhật UI trên EDT, khóa điều khiển trong từng màn hình khi đang chờ và ngăn thao tác lặp trong cùng màn hình. Database/service vẫn quyết định transaction và xử lý cạnh tranh.
- Không giữ transaction khi chờ hộp thoại xác nhận. Mỗi lần xác nhận phân bổ dùng đúng object preview đã xem; sau mỗi lần thử phải xem preview mới.
- Đóng cửa sổ bị chặn trong lúc một tác vụ còn chạy để người dùng có thể nhận kết quả.
- Đây là giao diện cho nghiệp vụ hiện có: phân bổ theo giá, nhu cầu cũ trước, đủ toàn bộ từng dòng. Không hiển thị score giả hoặc thêm công thức đánh giá supplier chưa chốt.
- Shop ID là ngữ cảnh demo nhập thủ công, chưa phải đăng nhập/phân quyền. Không bổ sung quản trị danh mục, sửa nguồn cung hoặc hủy đơn khi service tương ứng chưa có.
- Nhãn tiền VND theo quy ước console/demo hiện có; đơn vị lượng lấy từ sản phẩm. Lịch sử phản ánh ba tiêu chí do quán báo cáo, không phải đo độc lập thời gian giao hàng.
- Nếu kết nối mất ngay sau một thao tác ghi, kiểm tra lại danh sách trước khi gửi lại; UI không tự lặp yêu cầu ghi.

Kết quả kiểm chứng thực tế được ghi riêng trong `phase8-verification.md`.

## Chạy giao diện JavaFX của nhóm

Chạy `mvn javafx:run` bằng JDK 26 từ thư mục project. JavaFX và backend được biên dịch chung trong Maven. Lần chạy mặc định dùng dữ liệu demo.

Để thử luồng Buyer với PostgreSQL, chọn một database test có `database/01_schema.sql`, một shop đang hoạt động và ít nhất một sản phẩm đang hoạt động. Nếu database test mới chỉ có schema, có thể tạo dữ liệu tối thiểu như sau:

```sql
INSERT INTO coffee_shops (name) VALUES ('Integration Test Shop') RETURNING shop_id;
INSERT INTO coffee_products (name, unit) VALUES ('Integration Test Coffee', 'kg');
```

Trong PowerShell 7, đặt cấu hình database test và mã shop vừa chọn:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/coffee_test'
$env:DB_USER = Read-Host 'PostgreSQL user'
$env:DB_PASSWORD = Read-Host 'PostgreSQL password' -MaskInput
$env:COFFEE_TEST_SHOP_ID = Read-Host 'Test shop ID'
$env:COFFEE_TEST_BUYER_PASSWORD = Read-Host 'Test Buyer password' -MaskInput
mvn javafx:run
```

Đăng nhập bằng `buyer@coffeemongers.test`. Mật khẩu Buyer cần ít nhất 10 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt. Tạo demand, kiểm tra mã và trạng thái `OPEN`, rồi mở lại app trong cùng terminal để xem demand đã lưu trên dashboard. Chế độ PostgreSQL thử nghiệm chỉ áp dụng cho danh mục và demand của shop được cấu hình.

## Xem trước phân bổ theo tuần trong JavaFX

Dùng cùng database PostgreSQL thử nghiệm và cấu hình `DB_URL`, `DB_USER`, `DB_PASSWORD` như ở trên. Tạo mật khẩu riêng cho Admin thử nghiệm trong terminal trước khi mở app:

```powershell
$env:COFFEE_TEST_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Test Admin password' -AsSecureString)).Password
mvn javafx:run
```

Mật khẩu cần ít nhất 10 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt. Đăng nhập bằng `admin@coffeemongers.test` rồi mở **Allocation Preview**. Nhập năm ISO và tuần ISO cần hàng của demand. Hai giá trị này được lưu trong `demands.required_year` và `demands.required_week`. Nguồn cung của supplier cần nằm trong đúng tuần đó và đúng sản phẩm.

Bảng trên hiển thị các dòng demand đang mở và chưa được cấp. Chọn một dòng để xem số lượng đề xuất từ từng supplier. **Unallocated** là lượng chưa được đề xuất cấp cho dòng đó. **Supply Shortfall** là lượng thiếu so với nguồn cung còn tại lượt xử lý. Một dòng thiếu cung sẽ để lại toàn bộ lượng yêu cầu chưa được cấp theo chính sách đủ toàn bộ từng dòng. Màn preview chỉ đọc và dành cho Admin thử nghiệm.
