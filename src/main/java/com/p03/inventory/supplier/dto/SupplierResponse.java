package com.p03.inventory.supplier.dto;

public record SupplierResponse(
        Long id,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        boolean active
) {
}
