# Kiểm chứng Phase 8

Ngày kiểm chứng: 08/11/2026 (Asia/Saigon).

## Kết quả đã chạy

| Kiểm tra | Kết quả |
|---|---|
| Biên dịch bằng JDK 26.0.2.1 | 53 file Java production và 3 chương trình kiểm tra biên dịch thành công |
| `UiTaskCheck` | 33 kiểm tra đạt: worker ngoài EDT, callback trên EDT, giao diện vẫn phản hồi khi worker chờ, chặn thao tác lặp, khôi phục trạng thái điều khiển khi thành công/lỗi và cho phép thử lại |
| `PanelRenderCheck` | 21 kiểm tra đạt với dữ liệu PostgreSQL thật: tải danh mục, thêm dòng form, danh sách OPEN, preview, đổi tuần làm mất hiệu lực preview, chi tiết đơn, review cũ và lịch sử |
| `DesktopFlowCheck` | 28 kiểm tra đạt: danh mục active, tuần ISO, tạo demand nhiều dòng trùng sản phẩm với lượng thập phân, chặn preview cũ, xác nhận và chặn xác nhận lại, đọc đơn, kiểm tra đúng quán, hoàn tất, gửi review, chặn review trùng và cập nhật lịch sử |
| Bộ kiểm tra cấu hình database hiện có | 11/11 đạt; không thay đổi bộ nạp cấu hình |
| Lệnh `npm run desktop` | Chạy thành công qua launcher, biên dịch 53 file và đến `DesktopMain`; kiểm tra trong chế độ headless với cấu hình loopback giả, thoát 0 |
| Kiểm tra cú pháp launcher | JavaScript và PowerShell hợp lệ |
| Bảo toàn mã cũ | Đối chiếu SHA-256: 43 file Java gốc trong bản thử giữ nguyên |

Tổng: **93 kiểm tra đạt**, ngoài bước biên dịch và kiểm tra launcher.

## Môi trường thử

- PostgreSQL 18 với cluster riêng trong `.validation/phase8-desktop/pgdata`, chỉ nghe loopback ở cổng thử 55438.
- Database `coffee_phase8_test`, schema 13 bảng và seed mẫu được nạp mới trước khi kiểm tra.
- Không dùng thông tin đăng nhập hoặc ghi dữ liệu vào database ứng dụng thật. Server thử được tắt trong `finally` sau các lần chạy.
- pgJDBC 42.7.14 từ Maven cache.
- UI hiển thị múi giờ của **phiên JDBC thực tế**. JDBC có thể đặt múi giờ theo JVM, nên phép kiểm tra đối chiếu với `SHOW TIME ZONE` thay vì giả định luôn là UTC.

## Mức kiểm chứng giao diện

Năm panel được dựng offscreen ở 1200×720 với font Segoe UI 14 và dữ liệu đọc qua các nút thật, rồi xuất PNG để kiểm tra bố cục. Các thao tác ghi được kiểm chứng qua service trong database thử; chưa chạy kiểm tra bấm hộp thoại ghi trong cửa sổ Windows tương tác.

Các chương trình kiểm tra và ảnh nằm trong bản thử `.validation/phase8-desktop`. Kịch bản dùng ứng dụng trực tiếp được mô tả trong `phase8-desktop.md`.

Giới hạn chức năng theo backend hiện tại: Shop ID chọn thủ công; chưa có authentication, quản trị danh mục/nguồn cung hay công thức scoring mới. Phân bổ vẫn dùng quy tắc giá và nguồn cung của các phase trước.
