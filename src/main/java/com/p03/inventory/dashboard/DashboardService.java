package com.p03.inventory.dashboard;

import com.p03.inventory.dashboard.dto.DashboardResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getSummary() {
        return dashboardRepository.getSummary();
    }
}
