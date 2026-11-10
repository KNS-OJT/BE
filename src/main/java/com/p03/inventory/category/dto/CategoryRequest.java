package com.p03.inventory.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo / sửa nhóm hàng. active = false để ngừng sử dụng (không xóa vật lý). Bỏ trống active khi tạo = true. */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        Boolean active
) {
}
