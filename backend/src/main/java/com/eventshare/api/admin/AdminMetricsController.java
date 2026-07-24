package com.eventshare.api.admin;

import com.eventshare.api.admin.dto.AdminPerformanceResponse;
import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
public class AdminMetricsController {

    private final AdminMetricsService metricsService;

    public AdminMetricsController(AdminMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Operation(summary = "Runtime performance snapshot (admin)")
    @GetMapping("/performance")
    public AdminPerformanceResponse performance(@CurrentUser User admin) {
        return metricsService.performance(admin);
    }
}
