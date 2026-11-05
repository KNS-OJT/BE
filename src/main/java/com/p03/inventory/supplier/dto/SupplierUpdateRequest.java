package com.p03.inventory.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @NotBlank @Size(max = 200) String supplierName,
        @Size(max = 150) String contactPerson,
        @Size(max = 30) @Pattern(regexp = "^\\+?[0-9 ]{8,29}$") String phone,
        @Size(max = 254) @Email String email,
        @Size(max = 500) String address,
        @NotNull Boolean active
) {
}
