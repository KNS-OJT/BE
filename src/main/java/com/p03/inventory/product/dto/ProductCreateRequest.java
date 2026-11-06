package com.p03.inventory.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** IN02 / SCR-40. Tồn kho KHÔNG nhập ở đây: tạo sản phẩm tự sinh dòng tồn = 0; tồn đầu kỳ qua phiếu OPENING (BR-24). */
public record ProductCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9._-]{0,49}$",
                message = "SKU tối đa 50 ký tự, chỉ gồm CHỮ IN HOA, số, dấu . _ -") String sku,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 30) String unit
) {
}
