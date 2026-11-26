# Giao diện desktop: JavaFX và Swing

Cập nhật **26/11/2026**. JavaFX có các workspace Buyer/Supplier/Admin; Swing là
interface riêng vẫn dùng backend JDBC hiện có. Console có menu 0–13. Không nhầm
`npm run desktop` (Swing) với JavaFX.

## Chọn lệnh chạy

Chạy từ repository root chứa `pom.xml`:

| Interface | Lệnh PowerShell | Cấu hình |
| --- | --- | --- |
| JavaFX với database settings | `.\run-javafx.ps1` hoặc `npm.cmd run javafx` | JDK 26/Maven/Node 22.20+; database có sẵn và test identities. |
| JavaFX demo | `.\run-javafx.ps1 -Demo` | Dữ liệu/account session-only, không cần database. |
| JavaFX direct Maven | `mvn javafx:run` | JDK 26; tự đặt JDBC/test-account variables nếu dùng PostgreSQL. |
| Swing | `npm.cmd run desktop` | Environment adapter của npm nạp settings. |
| Swing direct script | `.\run-desktop.ps1` | Đã đặt `DB_URL`, `DB_USER`, `DB_PASSWORD`; pgJDBC đã có trong Maven cache. |
| Console | `npm.cmd run build` rồi `npm.cmd start` | Console chọn tuần qua menu 13. |

JavaFX entry point là `com.coffeemongers.App`; Swing là
`com.coffeeallocation.DesktopMain`; console là `com.coffeeallocation.Main`.
Backend Java chạy trong cùng ứng dụng; không có API server riêng cần mở thêm.
Launcher không start PostgreSQL, provision Neon, tạo schema hoặc seed.

## JavaFX và tài khoản PostgreSQL thử nghiệm

Theo [launcher guide](javafx-launcher.md), chuẩn bị database connection và các
account settings trước khi mở app. JavaFX launcher dùng environment adapter:
process `DATABASE_URL` → process JDBC triple → `.env` hoặc `.env.local`. Maven
trực tiếp không tự đọc các file đó.

| Login | Variables cần có trước startup |
| --- | --- |
| `buyer@coffeemongers.test` | `COFFEE_TEST_SHOP_ID` và `COFFEE_TEST_BUYER_PASSWORD` |
| `supplier@coffeemongers.test` | `COFFEE_TEST_SUPPLIER_ID` và `COFFEE_TEST_SUPPLIER_PASSWORD` |
| `admin@coffeemongers.test` | `COFFEE_TEST_ADMIN_PASSWORD` |

Passwords do người chạy đặt, ít nhất 10 ký tự với chữ hoa/thường/số/ký tự đặc
biệt. IDs lấy từ business records thực tế; tạo shop/supplier không tạo login.
Đổi ID/password cần restart; dùng Sign out để chuyển giữa các role đã bật.
Admin workspace previews không thay cho test Buyer/Supplier login.

`.\run-javafx.ps1 -Check` kiểm tra tools/settings, không kiểm tra database
reachability hoặc quyền sở hữu record. Khi đăng nhập test account, kiểm tra
badge `POSTGRESQL TEST`. Public registration và built-in demo Admin vẫn dùng
in-memory services; dữ liệu đó không chứng minh JDBC persistence.

## Luồng JavaFX hiện tại

1. **Supplier**: My Products offer một central product đang active; Weekly Supply
   khai báo quantity/price cho một tuần giao hàng hiện tại hoặc tương lai.
2. **Buyer**: Create Demand cho cùng sản phẩm/tuần, review rồi Submit Demand;
   My Demands/Demand Details đọc demand đã lưu. Submission chưa tạo orders.
3. **Admin**: Allocation Preview cho tuần đó; kiểm tra từng line và giá. Confirm
   Allocation áp dụng mọi line đủ cung trong batch tuần, không chỉ line đang chọn.
   Preview không reserve supply. Catch-up expiry có thể chạy trước read preview;
   transaction confirmation revalidate snapshot và ghi atomically.
4. **Supplier**: Refresh Incoming Orders và Weekly Supply để xem orders/remaining.
   Incoming Orders là chỉ đọc; Supplier không mark delivered.
5. **Buyer**: My Orders → Confirm received chuyển PENDING sang COMPLETED; Review
   supplier gửi ba Boolean criteria và comment. Service chặn sai ownership/status
   và review trùng.
6. **Admin**: Orders & Activity, Supplier Reviews và Overview đọc dữ liệu đã lưu.
   Dashboard ranking dùng review; allocation vẫn price-first. Supply composition
   poll riêng mỗi 5 giây khi dashboard hiển thị; không auto-refresh mọi view.

Chi tiết: [Buyer](buyer-workspace.md), [Supplier](supplier-workspace.md),
[Admin Overview](admin-overview.md), [ranking](supplier-ranking.md),
[expiry](demand-expiry.md) và [supply composition](supply-composition-chart.md).

## Swing: phạm vi và launcher

Swing launcher Windows compile backend/Swing vào `target/desktop-classes`, dùng
pgJDBC từ Maven cache; chạy `mvn compile` lần đầu nếu thiếu driver. Có thể chọn
JDK rõ ràng, thay đường dẫn theo máy:

```powershell
.\run-desktop.ps1 -JavaHome 'C:\Program Files\Java\jdk-26.0.2.1'
```

| Màn hình Swing | Chức năng |
| --- | --- |
| Nhu cầu | Tạo nhiều product lines; chọn ISO delivery year/week; xem OPEN demands. |
| Phân bổ & nguồn cung | Shared preview, chi tiết supply/price, confirm mọi line đủ cung của tuần. |
| Đơn hàng | Danh sách/details, receipt với đúng shop ID. |
| Đánh giá | Xem/gửi một review cho completed order của shop đã nhập. |
| Lịch sử supplier | Hợp lệ theo cutoff, counts/rates và database session timezone. |

Shop ID trong Swing là ngữ cảnh nhập thủ công, không phải JavaFX authentication.
Swing không có toàn bộ Admin catalog/Supplier workspace của JavaFX. `UiTasks`
chạy service ngoài EDT; UI disable controls khi pending và giữ backend transaction
rules. Giá VND theo demo convention, quantity unit lấy từ product.

## Chọn tuần và dữ liệu demo

Dùng database demo mới với `01_schema.sql` rồi **một** seed: ưu tiên
`04_current_demo_data.sql` cho current/next week. `02_seed_data.sql` giữ lịch sử
41–43/2026 và không phù hợp tạo/allocate demand mới khi các tuần đó đã qua.
Không chạy seed vào database đã có dữ liệu hoặc dịch chuyển records lịch sử.

Trong Swing, chọn cùng delivery ISO year/week cho demand và allocation; trong
JavaFX dùng date/week selector; trong console dùng menu 13. Backend vẫn chặn
tuần quá hạn theo `Asia/Ho_Chi_Minh`, bất kể interface. Nếu thiếu nguồn cung,
line không nhận partial allocation; dòng khác đủ cung vẫn có thể confirm.
Xem [rolling demo guide](current-demo.md) cho fixture và expected results.

Khi preview thay đổi, load và xem lại trước khi confirm. Khi mất kết nối sau
write, refresh orders/demands/supply để xác định kết quả trước khi gửi lại;
không tự retry approval hoặc dùng UI Undo để hoàn tác database.

## Kiểm chứng và phạm vi bằng chứng

[phase8-verification.md](phase8-verification.md) là báo cáo Swing ngày 08/11/2026,
không phải kết quả chạy lại JavaFX hiện tại. Các feature docs có ngày kiểm chứng
riêng; [README](../README.md) liệt kê test commands và integration flags.

Bản cập nhật tài liệu này không chạy UI, database writes hoặc full regression.
Cấu hình thật và workflow cần được kiểm tra trên database demo riêng trước buổi
trình bày; một successful `-Check` chưa chứng minh kết nối/persistence.
