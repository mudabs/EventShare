package com.eventshare.api.subscription;

import com.eventshare.api.subscription.dto.PlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanService {

    private final PlanRepository plans;

    public PlanService(PlanRepository plans) {
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActive() {
        return plans.findByActiveTrueOrderByPriceCentsAsc().stream().map(PlanResponse::from).toList();
    }
}
