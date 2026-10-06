package com.eventshare.api.report.dto;

import com.eventshare.api.report.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportMediaRequest(
        @NotNull ReportReason reason,
        @Size(max = 500) String details
) {
}
