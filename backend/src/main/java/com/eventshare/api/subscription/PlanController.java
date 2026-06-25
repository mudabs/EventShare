package com.eventshare.api.subscription;

import com.eventshare.api.subscription.dto.PlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Plans")
@RestController
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @Operation(summary = "List active plans (public, for the pricing page)")
    @GetMapping("/api/plans")
    public List<PlanResponse> plans() {
        return planService.listActive();
    }
}
