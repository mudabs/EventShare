package com.eventshare.api.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces the Privacy Policy promise that raw IP addresses in the audit log are kept for a
 * limited time ({@code eventshare.privacy.audit-ip-retention-days}, default 90). Older rows
 * keep the action and timestamp; only the IP is cleared. Runs daily.
 */
@Component
public class AuditIpRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditIpRetentionJob.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public AuditIpRetentionJob(JdbcTemplate jdbc,
                               @Value("${eventshare.privacy.audit-ip-retention-days:90}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${eventshare.privacy.audit-ip-cron:0 30 3 * * *}")
    public void purgeOldIpAddresses() {
        try {
            int cleared = jdbc.update(
                    "UPDATE audit_logs SET ip_address = NULL "
                            + "WHERE ip_address IS NOT NULL AND created_at < now() - make_interval(days => ?)",
                    retentionDays);
            if (cleared > 0) {
                log.info("Cleared IP addresses from {} audit rows older than {} days", cleared, retentionDays);
            }
        } catch (RuntimeException e) {
            log.error("Audit IP retention job failed: {}", e.getMessage(), e);
        }
    }
}
