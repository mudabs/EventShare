package com.eventshare.api.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the demo when {@code eventshare.demo.enabled=true}:
 * <ul>
 *   <li>On startup (in a background thread, so readiness is not delayed): seed the
 *       showcase if it is missing, otherwise just re-apply account passwords.</li>
 *   <li>On {@code eventshare.demo.reset-cron} (default 04:00 America/Chicago): full
 *       reset so each interviewer starts from the same state.</li>
 * </ul>
 * Failures are logged, never rethrown: a broken demo must not take the API down.
 */
@Component
public class DemoLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DemoLifecycle.class);

    private final DemoProperties props;
    private final DemoSeeder seeder;

    public DemoLifecycle(DemoProperties props, DemoSeeder seeder) {
        this.props = props;
        this.seeder = seeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.enabled()) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                if (seeder.isSeeded()) {
                    seeder.syncAccounts();
                    log.info("Demo already seeded (invite code {}); demo accounts synced", props.inviteCode());
                } else {
                    seeder.reset("startup");
                }
            } catch (RuntimeException e) {
                log.error("Demo startup seeding failed: {}", e.getMessage(), e);
            }
        }, "demo-seeder");
        worker.setDaemon(true);
        worker.start();
    }

    @Scheduled(cron = "${eventshare.demo.reset-cron:0 0 4 * * *}",
            zone = "${eventshare.demo.reset-zone:America/Chicago}")
    public void nightlyReset() {
        if (!props.enabled()) {
            return;
        }
        try {
            seeder.reset("scheduled");
        } catch (RuntimeException e) {
            log.error("Scheduled demo reset failed: {}", e.getMessage(), e);
        }
    }
}
