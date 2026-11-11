# Abstract catalog DAO

`com.coffeeallocation.dao.AbstractCatalogDao<T>` là abstract class dùng chung cho
`CoffeeProductDao` và `CoffeeShopDao`. `T` lần lượt là `CoffeeProduct` và `CoffeeShop`.

Lớp cha triển khai `public List<T> findActive()` theo một quy trình chung:
tạo prepared statement, bind `is_active = true`, đọc từng dòng, gọi `mapRow`,
và đóng result set cùng statement bằng try-with-resources.

Hai abstract methods do lớp con ghi đè:

- `protected String activeQuery()`: trả về câu SQL cố định của từng danh mục.
- `protected T mapRow(ResultSet row) throws SQLException`: chuyển một dòng thành model tương ứng.

Constructor của lớp cha là `protected`; lớp cha không được khởi tạo trực tiếp.
Kết nối được lưu trong thuộc tính `private final`, và lớp con truy cập cùng kết nối
qua `protected final Connection connection()`.

Đây là cách dùng Template Method: quy trình truy vấn ở lớp cha, các bước đặc thù
ở lớp con. Call site vẫn dùng `new CoffeeProductDao(connection).findActive()` hoặc
`new CoffeeShopDao(connection).findActive()` như trước.

SQL, thứ tự kết quả, kiểu model và logic `lockActiveById()` được giữ nguyên.
Service tiếp tục sở hữu connection và transaction; DAO không commit, rollback,
đóng connection hay tự mở kết nối khác. Không thay đổi schema hoặc thuật toán phân bổ.

## Kiểm chứng ngày 11/11/2026

- `mvn --batch-mode --offline test`: biên dịch 118 file production; 9 tests đạt,
  không có lỗi hoặc test bị bỏ qua. Bao gồm kiểm tra tải 20 FXML và điều hướng.
- PostgreSQL 18 trên cluster thử mới, chỉ nghe loopback: 28 kiểm tra luồng
  danh mục, tạo demand, preview, xác nhận, order, hoàn tất, review và lịch sử đạt.
- Thêm 12 kiểm tra hồi quy với database thật: mapping và thứ tự danh mục,
  lọc active, danh mục rỗng, kết nối do caller sở hữu, yêu cầu transaction khi khóa,
  đọc cùng transaction và giữ khả năng rollback, chặn demand với shop/product
  inactive và không để lại dữ liệu dở dang.
- Database thử dùng schema và seed của repository, được tắt sau kiểm tra;
  không dùng database ứng dụng thật.

Đối chiếu hash xác nhận chỉ hai file Java hiện có là `CoffeeProductDao` và
`CoffeeShopDao` được chỉnh sửa trong lần thay đổi này. Các thay đổi khác đã có
trước trong repository được giữ nguyên.
