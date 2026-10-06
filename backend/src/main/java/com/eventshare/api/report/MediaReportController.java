package com.eventshare.api.report;

import com.eventshare.api.common.util.ClientIp;
import com.eventshare.api.report.dto.ReportMediaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Reports")
@RestController
public class MediaReportController {

    private final MediaReportService reports;

    public MediaReportController(MediaReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "Report a photo or video in a gallery (public, by invite code)")
    @PostMapping("/api/events/code/{code}/media/{mediaId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable String code, @PathVariable UUID mediaId,
                       @Valid @RequestBody ReportMediaRequest request, HttpServletRequest httpRequest) {
        reports.report(code, mediaId, request, ClientIp.resolve(httpRequest));
    }
}
