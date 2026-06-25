package com.eventshare.api.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Records security- and moderation-relevant actions. Writes join the caller's
 * transaction so an audit row is atomic with the operation it describes and may
 * safely reference rows created in that same transaction (e.g. a new event).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(UUID eventId, UUID actorUserId, String actorLabel, String action,
                       String targetType, UUID targetId, Map<String, Object> metadata, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setEventId(eventId);
        log.setActorUserId(actorUserId);
        log.setActorLabel(actorLabel);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setMetadata(metadata);
        log.setIpAddress(ipAddress);
        repository.save(log);
    }
}
