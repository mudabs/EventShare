package com.eventshare.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when a completed upload fails server-side verification (for example the
 * object in R2 is larger than the size the client declared). Transactions that
 * throw it are configured with {@code noRollbackFor} so the media row can be
 * durably marked FAILED/DELETED before the error reaches the client.
 */
public class UploadRejectedException extends ApiException {
    public UploadRejectedException(String message) {
        super(HttpStatus.BAD_REQUEST, "upload_rejected", message);
    }
}
