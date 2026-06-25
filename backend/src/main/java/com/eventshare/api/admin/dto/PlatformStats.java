package com.eventshare.api.admin.dto;

import java.util.List;

public record PlatformStats(
        long totalUsers,
        long totalEvents,
        long totalUploads,
        long totalStorageBytes,
        List<MonthCount> monthlyGrowth
) {
}
