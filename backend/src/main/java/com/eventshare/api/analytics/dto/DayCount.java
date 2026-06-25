package com.eventshare.api.analytics.dto;

import java.time.LocalDate;

public record DayCount(LocalDate date, long count) {
}
