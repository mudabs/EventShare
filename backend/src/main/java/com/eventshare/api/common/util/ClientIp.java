package com.eventshare.api.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * Resolves the originating client IP. Behind nginx the real address arrives in
 * X-Forwarded-For; we take the first hop and fall back to the socket address.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = trimToNull(request.getRemoteAddr());
        if (remoteAddr == null) {
            return "unknown";
        }

        // Only trust proxy-forwarded headers when the direct peer is a trusted
        // reverse proxy (loopback/private RFC1918 or ULA/link-local range).
        if (isTrustedProxyAddress(remoteAddr)) {
            String realIp = trimToNull(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }

            String forwarded = trimToNull(request.getHeader("X-Forwarded-For"));
            if (forwarded != null) {
                int comma = forwarded.indexOf(',');
                String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
                String forwardedIp = trimToNull(first);
                if (forwardedIp != null) {
                    return forwardedIp;
                }
            }
        }

        return remoteAddr;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isTrustedProxyAddress(String ip) {
        String value = ip.toLowerCase(Locale.ROOT);

        // Loopback
        if (value.equals("127.0.0.1") || value.equals("::1")) {
            return true;
        }

        // RFC1918 private ranges
        if (value.startsWith("10.") || value.startsWith("192.168.")) {
            return true;
        }
        if (value.startsWith("172.")) {
            String[] parts = value.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        // IPv6 unique-local/link-local
        return value.startsWith("fc") || value.startsWith("fd") || value.startsWith("fe80:");
    }
}
