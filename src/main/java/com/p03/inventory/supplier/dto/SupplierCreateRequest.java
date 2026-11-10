package com.p03.inventory.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** IN03. Nhà cung cấp mới luôn ở trạng thái đang hợp tác. Email theo đúng CHECK ck_suppliers_email của DB. */
public record SupplierCreateRequest(
        @NotBlank @Size(max = 200) String supplierName,
        @Size(max = 150) String contactPerson,
        @Size(max = 30) @Pattern(regexp = "^\\+?[0-9 ]{8,29}$",
                message = "Số điện thoại 8–29 chữ số, cho phép dấu + ở đầu và khoảng trắng") String phone,
        @Size(max = 254) @Email @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                message = "Email không hợp lệ") String email,
        @Size(max = 500) String address
) {
}
