package com.syncnest.userservice.logging;

/**
 * Reduces PII in log lines (emails, IPs, user agents). Values remain suitable for correlation, not identification.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    public static String maskEmail(String email) {
        if (email == null || email.length() < 3) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***@***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /** IPv4: keep first two octets; IPv6: short prefix only. */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "***";
        }
        String trimmed = ip.trim();
        if (trimmed.contains(".")) {
            String[] parts = trimmed.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
            return "***";
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            int end = Math.min(trimmed.length(), 12);
            return trimmed.substring(0, end) + "…";
        }
        return "***";
    }

    /** Truncates UA and appends total length for debugging without storing full strings in logs. */
    public static String maskUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "";
        }
        int len = userAgent.length();
        int keep = Math.min(32, len);
        String head = userAgent.substring(0, keep).replace('\n', ' ').replace('\r', ' ');
        return len <= keep ? head : head + "…(len=" + len + ")";
    }
}
