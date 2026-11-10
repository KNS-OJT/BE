package com.p03.inventory.dashboard.dto;

public record DashboardResponse(
    Long activeProducts,
    Long lowStockProducts,
    Long prWaitingApproval,
    Long poWaitingReceipt,
    Long issuesWaitingConfirm
) {
}
