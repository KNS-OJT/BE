package com.p03.inventory.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardResponse getSummary() {

        String sql = """
            SELECT
                active_products,
                low_stock_products,
                pr_waiting_approval,
                po_waiting_receipt,
                issues_waiting_confirm
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
            )
        );
    }
}
