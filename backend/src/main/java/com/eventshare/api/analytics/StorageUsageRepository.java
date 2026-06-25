package com.eventshare.api.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StorageUsageRepository extends JpaRepository<StorageUsage, UUID> {
}
