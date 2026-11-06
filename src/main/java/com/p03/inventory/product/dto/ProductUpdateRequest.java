package com.p03.inventory.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Sửa sản phẩm. active = false để ngừng kinh doanh (không xóa). SKU không đổi được khi đã dùng trong chứng từ (BR-01). */
public record ProductUpdateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9._-]{0,49}$",
                message = "SKU tối đa 50 ký tự, chỉ gồm CHỮ IN HOA, số, dấu . _ -") String sku,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 30) String unit,
        @NotNull Boolean active
) {
}
