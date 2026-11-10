package com.p03.inventory.product.dto;

import com.p03.inventory.product.Product;

import java.time.Instant;

/** Không có số tồn: tồn kho chỉ Warehouse/Manager xem qua /api/stock (IN04). */
public record ProductResponse(Long id, String sku, String name, Long categoryId, String categoryName,
                              String unit, boolean active, Instant createdAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory().getId(),
                p.getCategory().getName(), p.getUnit(), p.isActive(), p.getCreatedAt());
    }
}
