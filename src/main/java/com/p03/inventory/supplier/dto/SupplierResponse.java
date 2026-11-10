package com.p03.inventory.supplier.dto;

import com.p03.inventory.supplier.Supplier;

public record SupplierResponse(
        Long id,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        boolean active
) {

    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.isActive());
    }
}
