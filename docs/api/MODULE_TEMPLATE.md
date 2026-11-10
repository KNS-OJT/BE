# Mẫu contract cho từng module

Owner: <điền>. Module: <điền>. Version: <điền>. Status: proposed / implemented / agreed. PR: <điền>.

1. Liệt kê từng method/path, operationId duy nhất, trạng thái triển khai.
2. Ghi role + ownership + SoD + trạng thái cho phép; không chỉ ghi “cần đăng nhập”.
3. Định nghĩa request DTO riêng theo action: required, nullable, giới hạn, unknown fields; không nhận actor/status do server quản lý.
4. Response thành công, HTTP, ví dụ hợp lệ; số lượng decimal; phân trang thống nhất items/page/size/totalElements/totalPages.
5. Lỗi dùng ErrorCode chung: status/code/message/requestId, details tùy trường hợp. Không tự đặt code khác trong nhánh module.
6. Audit và ảnh hưởng dữ liệu/tồn kho, transaction, request lặp và cạnh tranh nếu có.
7. Postman: biến baseUrl/token/entityId; tự lưu ID response; mật khẩu và token để trống khi chia sẻ. Positive/negative tests phải kiểm tra đúng HTTP/code; 401 không thay thế kết quả 403/404.
8. Đính kèm kết quả runtime thật; phân biệt expectation với evidence.
9. Điểm chưa chốt, người quyết định, ngày/link xác nhận.

## Khung OpenAPI tổng hợp

Dùng components/securitySchemes/bearerAuth và schemas/Error trong si-draft.openapi.json làm mẫu. Từng owner bổ sung paths/schema/tag/operationId cho module của mình sau review. Không ghép file PROPOSED vào spec đã triển khai. Tránh trùng operationId/schema; đối chiếu `/v3/api-docs` của backend tích hợp trước phát hành.

Module owners theo phân công đã cung cấp: Minh Auth/User/Product/Category/common; Việt Anh PR; Trang Supplier/PO; Hoàng Anh GR/SA; Trung Hiếu Stock/Dashboard; Khoa SI và tổng hợp OpenAPI/Postman. Khung này chưa đại diện contract hoàn chỉnh của các module khác.
