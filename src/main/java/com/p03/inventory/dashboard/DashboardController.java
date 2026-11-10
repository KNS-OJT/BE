package com.p03.inventory.dashboard;

import com.p03.inventory.dashboard.dto.DashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.p03.inventory.common.security.Authz;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize(Authz.AUTHENTICATED)
    public DashboardResponse getSummary() {
        return dashboardService.getSummary();
    }
}
