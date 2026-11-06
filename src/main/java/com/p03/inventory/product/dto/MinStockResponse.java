package com.p03.inventory.product.dto;

import java.math.BigDecimal;

public record MinStockResponse(Long productId, String sku, BigDecimal minimumStock) {
}
