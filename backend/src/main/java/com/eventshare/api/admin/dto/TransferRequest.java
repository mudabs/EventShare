package com.eventshare.api.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferRequest(@NotNull UUID newOwnerUserId) {
}
