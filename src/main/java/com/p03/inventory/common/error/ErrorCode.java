package com.p03.inventory.common.error;

import org.springframework.http.HttpStatus;

/**
 * Bảng mã lỗi thống nhất của API (System Security Design mục 4, SecRS IA-NF04).
 * <p>
 * KHÔNG tự thêm mã mới trong nhánh của mình: muốn thêm thì mở PR riêng sửa file này để cả nhóm review,
 * vì FE và test của IA bám theo đúng danh sách này.
 */
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    UNKNOWN_FIELD(HttpStatus.BAD_REQUEST, "Request chứa trường không được phép"),

    // 401
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc phiên không hợp lệ"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),

    // 403
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    SOD_VIOLATION(HttpStatus.FORBIDDEN, "Không được tự phê duyệt/xác nhận chứng từ do chính mình tạo"),

    // 404 – dùng cả khi đối tượng tồn tại nhưng người gọi không có quyền xem (không lộ sự tồn tại)
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),

    // 409
    INVALID_STATE(HttpStatus.CONFLICT, "Chứng từ không ở trạng thái cho phép thao tác này"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Số lượng xuất vượt quá tồn kho khả dụng"),
    OVER_RECEIPT(HttpStatus.CONFLICT, "Số lượng nhận vượt quá số lượng còn lại của đơn hàng"),
    DUPLICATE(HttpStatus.CONFLICT, "Dữ liệu bị trùng"),

    // 429
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại sau");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
