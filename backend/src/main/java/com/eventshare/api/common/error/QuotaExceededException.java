package com.eventshare.api.common.error;

import org.springframework.http.HttpStatus;

/** Raised when a plan limit (events, uploads, or storage) is reached. */
public class QuotaExceededException extends ApiException {
    public QuotaExceededException(String message) {
        super(HttpStatus.FORBIDDEN, "quota_exceeded", message);
    }
}
