package com.p03.inventory.dashboard;

import com.p03.inventory.dashboard.dto.DashboardResponse;
import com.p03.inventory.common.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardResponse getSummary(Role role, Long userId) {

        String sql = """
            SELECT
                CASE WHEN ? IN ('MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING_STAFF')
                     THEN active_products ELSE 0 END AS active_products,
                CASE WHEN ? IN ('MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING_STAFF')
                     THEN low_stock_products ELSE 0 END AS low_stock_products,
                CASE WHEN ? = 'MANAGER' THEN pr_waiting_approval
                     WHEN ? = 'REQUESTER' THEN
                         (SELECT count(*) FROM purchase_requests WHERE requester_id = ? AND status = 'SUBMITTED')
                     ELSE 0 END AS pr_waiting_approval,
                CASE WHEN ? IN ('MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING_STAFF')
                     THEN po_waiting_receipt ELSE 0 END AS po_waiting_receipt,
                CASE WHEN ? IN ('MANAGER', 'WAREHOUSE_STAFF') THEN issues_waiting_confirm
                     WHEN ? IN ('REQUESTER', 'PURCHASING_STAFF') THEN
                         (SELECT count(*) FROM stock_issues WHERE requested_by = ? AND status = 'DRAFT')
                     ELSE 0 END AS issues_waiting_confirm
            FROM v_dashboard_summary
            """;

        return jdbcTemplate.queryForObject(
            sql,
            (rs, rowNum) -> new DashboardResponse(
                rs.getLong("active_products"),
                rs.getLong("low_stock_products"),
                rs.getLong("pr_waiting_approval"),
                rs.getLong("po_waiting_receipt"),
                rs.getLong("issues_waiting_confirm")
            ),
            role.name(), role.name(), role.name(), role.name(), userId,
            role.name(), role.name(), role.name(), userId
        );
    }
}
