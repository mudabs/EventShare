package com.eventshare.api.media.dto;

import com.eventshare.api.media.ModerationAction;
import jakarta.validation.constraints.NotNull;

public record ModerationRequest(@NotNull ModerationAction action) {
}
