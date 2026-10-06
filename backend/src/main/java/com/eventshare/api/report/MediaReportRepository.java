package com.eventshare.api.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MediaReportRepository extends JpaRepository<MediaReport, UUID> {

    boolean existsByMediaIdAndReporterIpHash(UUID mediaId, String reporterIpHash);

    long countByMediaIdAndResolvedAtIsNull(UUID mediaId);

    /** Host reviewed the photo (restored or kept it): close its open reports. */
    @Modifying
    @Query("update MediaReport r set r.resolvedAt = :now where r.mediaId = :mediaId and r.resolvedAt is null")
    int resolveOpen(@Param("mediaId") UUID mediaId, @Param("now") Instant now);
}
