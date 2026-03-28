package com.syncnest.userservice.utils;

import com.syncnest.userservice.entity.DeviceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extracts real-time device/network context directly from the HTTP request.
 * IP is always resolved server-side (never trusted from the request body).
 * User-Agent is parsed to derive device type, OS, and browser.
 */
@Component
public class RequestMetadataExtractor {

    // Ordered by reliability; X-Forwarded-For is most common behind proxies/load balancers
    private static final List<String> IP_HEADERS = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Extracts the real client IP, handling proxy chains. */
    public String extractIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For may be "client, proxy1, proxy2" — take the first (original client)
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** Returns the raw User-Agent header, or "Unknown" if absent. */
    public String extractUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return (ua != null && !ua.isBlank()) ? ua : "Unknown";
    }

    /** Classifies the device type from the User-Agent string. */
    public DeviceType parseDeviceType(String ua) {
        if (ua == null) return DeviceType.UNKNOWN;
        String lower = ua.toLowerCase();
        if (lower.contains("bot") || lower.contains("crawler")
                || lower.contains("spider") || lower.contains("slurp")
                || lower.contains("googlebot") || lower.contains("bingbot")) {
            return DeviceType.BOT;
        }
        if (lower.contains("ipad") || lower.contains("tablet") || lower.contains("kindle")) {
            return DeviceType.TABLET;
        }
        if (lower.contains("mobile") || lower.contains("iphone") || lower.contains("ipod")
                || lower.contains("windows phone") || lower.contains("android")
                && lower.contains("mobile")) {
            return DeviceType.MOBILE;
        }
        if (lower.contains("windows") || lower.contains("macintosh")
                || lower.contains("x11") || (lower.contains("linux") && !lower.contains("android"))) {
            return DeviceType.DESKTOP;
        }
        return DeviceType.UNKNOWN;
    }

    /** Parses OS name+version from the User-Agent string. */
    public String parseOs(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";

        if (ua.contains("Windows NT 10.0") || ua.contains("Windows NT 11.0")) return "Windows 10/11";
        if (ua.contains("Windows NT 6.3")) return "Windows 8.1";
        if (ua.contains("Windows NT 6.2")) return "Windows 8";
        if (ua.contains("Windows NT 6.1")) return "Windows 7";
        if (ua.contains("Windows")) return "Windows";

        if (ua.contains("iPhone OS") || ua.contains("CPU OS")) {
            String v = extractSegment(ua, "CPU OS ", ' ', ';').replace('_', '.');
            return v.isEmpty() ? "iOS" : "iOS " + v;
        }
        if (ua.contains("Mac OS X")) {
            String v = extractSegment(ua, "Mac OS X ", ' ', ')').replace('_', '.');
            return v.isEmpty() ? "macOS" : "macOS " + v;
        }
        if (ua.contains("Android")) {
            String v = extractSegment(ua, "Android ", ';', ')');
            return v.isEmpty() ? "Android" : "Android " + v;
        }
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("CrOS")) return "ChromeOS";
        return "Unknown";
    }

    /** Parses browser name from the User-Agent string. */
    public String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        // Order matters — check specific/branded tokens before generic ones
        if (ua.contains("PostmanRuntime")) return "Postman";
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgIOS/")) return "Microsoft Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser/")) return "Samsung Browser";
        if (ua.contains("YaBrowser/")) return "Yandex Browser";
        if (ua.contains("Chrome/") && !ua.contains("Chromium/")) return "Chrome";
        if (ua.contains("Chromium/")) return "Chromium";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome")) return "Safari";
        if (ua.contains("MSIE") || ua.contains("Trident/")) return "Internet Explorer";
        return "Unknown";
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Extracts a segment of text from {@code ua} starting after {@code prefix},
     * stopping at the first occurrence of {@code stopA} or {@code stopB}.
     */
    private String extractSegment(String ua, String prefix, char stopA, char stopB) {
        int idx = ua.indexOf(prefix);
        if (idx == -1) return "";
        String sub = ua.substring(idx + prefix.length());
        StringBuilder buf = new StringBuilder();
        for (char c : sub.toCharArray()) {
            if (c == stopA || c == stopB) break;
            buf.append(c);
        }
        return buf.toString().trim();
    }
}

