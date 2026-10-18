# Kết quả kiểm chứng

Ngày: 15/10/2026.

Đã chạy trực tiếp với **PostgreSQL 18.1, Windows x64** trong một cluster kiểm thử tách biệt, chỉ lắng nghe localhost. Không sử dụng hoặc sửa database đang có của người dùng. Server kiểm thử đã dừng sau khi chạy.

## Kết quả

- `01_schema.sql`: chạy thành công và COMMIT; xác nhận đúng 13 bảng trong public schema.
- `03_test_queries.sql`: chạy thành công, báo PASS và ROLLBACK.
- Luồng hợp lệ: shop → supplier/product → weekly supply → demand/item → run/score → allocation → order/item → Boolean review.
- Query tìm supplier khớp product, tuần/năm và active flags trả đúng kết quả.
- Giá trị `total_price` được sinh đúng từ quantity × unit_price.
- Từ chối 18 trường hợp sai: supplier-product trùng; supply tuần trùng; remaining âm; remaining vượt initial; NaN quantity; ISO week không tồn tại ở supply; ISO week không tồn tại ở demand; demand quantity bằng 0; run/item không khớp; supplier của order không khớp allocation; supplier của review không khớp order; xóa supplier đang được tham chiếu; score âm; score run/supplier trùng; status rỗng; shop name NULL; total tiền vượt precision; order quantity âm sau một thao tác trừ supply.
- Chấp nhận tuần ISO 53 của năm 2026; từ chối tuần 53 của năm 2025.
- Lỗi ghi order item trong subtransaction hoàn tác thay đổi supply trước đó.
- Sau ROLLBACK, truy vấn coffee_shops trả 0 dòng fixture.

## Giới hạn

Đây là kiểm chứng DDL và constraints, không phải kiểm thử Java/JDBC hoặc concurrency với hai kết nối xác nhận đồng thời. Chưa có API xác nhận allocation, idempotency, state machine, scoring algorithm hoặc bộ seed hoàn chỉnh. Các quy tắc liên bảng và aggregate chưa được DDL bảo vệ được liệt kê rõ trong README.md.

Các dòng TEST_* chỉ dùng để chứng minh status không bị tự khóa vào một workflow chưa thống nhất. Không coi chúng là trạng thái nghiệp vụ chính thức.
