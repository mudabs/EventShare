package com.eventshare.api.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP. Behind nginx the real address arrives in
 * X-Forwarded-For; we take the first hop and fall back to the socket address.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            if (!first.isBlank()) {
                return first.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
