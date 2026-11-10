package com.p03.inventory.po;

/**
 * Khớp enum PostgreSQL {@code purchase_order_status}. Luồng: DRAFT → ORDERED → PARTIALLY_RECEIVED → RECEIVED;
 * DRAFT|ORDERED → CANCELED. PARTIALLY_RECEIVED / RECEIVED do trigger nhận hàng tự đặt (BR-21), API không đặt.
 */
public enum PoStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELED
}
