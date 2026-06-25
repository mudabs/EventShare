package com.eventshare.worker.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaRecordRepository extends JpaRepository<MediaRecord, UUID> {
}
