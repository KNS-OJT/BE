package com.p03.inventory.pr;

/**
 * Trạng thái phiếu yêu cầu mua hàng – khớp PostgreSQL ENUM purchase_request_status.
 * Luồng hợp lệ: DRAFT → SUBMITTED → APPROVED / REJECTED; DRAFT/SUBMITTED → CANCELED.
 */
public enum PrStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELED
}

