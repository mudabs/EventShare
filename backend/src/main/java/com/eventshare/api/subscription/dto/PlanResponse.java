package com.eventshare.api.subscription.dto;

import com.eventshare.api.subscription.Plan;

public record PlanResponse(
        String code,
        String name,
        int priceCents,
        String billingInterval,
        Integer maxEvents,
        Integer maxGuestsPerEvent,
        Integer maxPhotosPerEvent,
        Integer maxVideosPerEvent,
        Long storageBytes,
        boolean zipExport,
        boolean advancedAnalytics,
        boolean priorityProcessing,
        Integer retentionMonths
) {
    public static PlanResponse from(Plan p) {
        return new PlanResponse(p.getCode(), p.getName(), p.getPriceCents(), p.getBillingInterval(),
                p.getMaxEvents(), p.getMaxGuestsPerEvent(), p.getMaxPhotosPerEvent(), p.getMaxVideosPerEvent(),
                p.getStorageBytes(), p.isZipExport(), p.isAdvancedAnalytics(), p.isPriorityProcessing(),
                p.getRetentionMonths());
    }
}
