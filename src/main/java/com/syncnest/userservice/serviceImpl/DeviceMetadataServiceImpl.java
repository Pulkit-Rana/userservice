package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.DeviceMetadataRepository;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.DeviceMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMetadataServiceImpl implements DeviceMetadataService {

    private final DeviceMetadataRepository deviceMetadataRepository;
    private final UserRepository userRepository;

    // Java 11+ HttpClient — no extra deps needed
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    // ─── Public API ──────────────────────────────────────────────────────────

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertDeviceLogin(User user, DeviceContext ctx) {
        try {
            log.debug("Async device metadata upsert started for user={}, ip={}, ua={}", 
                    user.getEmail(), ctx.getIp(), ctx.getUserAgent());

            // Re-fetch user in the new isolated transaction to avoid LazyInit issues
            User managedUser = userRepository.findByIdAndDeletedAtIsNull(user.getId()).orElse(null);
            if (managedUser == null) {
                log.warn("upsertDeviceLogin: user not found id={}", user.getId());
                return;
            }

            String location = resolveLocation(ctx.getIp()); // blocking but already async
            String fingerprint = buildFingerprint(ctx);

            deviceMetadataRepository.findByUserAndDeviceId(managedUser, fingerprint)
                    .ifPresentOrElse(
                            existing -> updateExisting(existing, ctx, location),
                            () -> insertNew(managedUser, ctx, fingerprint, location)
                    );
        } catch (Exception ex) {
            // Never block the auth flow due to device tracking failure
            log.warn("Failed to upsert device metadata for user={}: {}", user.getEmail(), ex.getMessage(), ex);
        }
    }

    // ...existing code...

    private void updateExisting(DeviceMetadata device, DeviceContext ctx, String location) {
        log.debug("Updating existing device metadata: deviceId={}, newIp={}, newLocation={}", 
                device.getDeviceId(), ctx.getIp(), location);
        device.setIpAddress(ctx.getIp());
        device.setLocation(location);
        device.setLastLoginAt(LocalDateTime.now());
        // Re-save updated device info (OS/browser/UA generally don't change for the same device)
        deviceMetadataRepository.save(device);
        log.info("Updated device metadata id={}, user={}", device.getId(), device.getUser().getEmail());
    }

    private void insertNew(User user, DeviceContext ctx, String fingerprint, String location) {
        log.debug("Inserting new device metadata for user={}, fingerprint={}, os={}, browser={}", 
                user.getEmail(), fingerprint, ctx.getOs(), ctx.getBrowser());

        DeviceMetadata device = DeviceMetadata.builder()
                .user(user)
                .deviceId(fingerprint)
                .ipAddress(ctx.getIp())
                .userAgent(ctx.getUserAgent())
                .os(ctx.getOs())
                .browser(ctx.getBrowser())
                .deviceType(ctx.getDeviceType() != null ? ctx.getDeviceType() : DeviceType.UNKNOWN)
                .provider(ctx.getProvider() != null ? ctx.getProvider() : AuthProvider.LOCAL)
                .location(location)
                .firstSeenAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .build();
        deviceMetadataRepository.save(device);
        log.info("Registered new device for user={} fingerprint={} os={} browser={} location={}",
                user.getEmail(), fingerprint, ctx.getOs(), ctx.getBrowser(), location);
    }

    /**
     * Resolves a human-readable location string from an IP address using ip-api.com
     * (free, no API key, ~100ms typical round-trip).
     * Returns "Local Network" for private/loopback IPs, "Unknown" on any failure.
     */
    private String resolveLocation(String ip) {
        if (ip == null || ip.isBlank() || isPrivateIp(ip)) {
            log.debug("Location resolution skipped for IP: {} (private or null)", ip);
            return "Local Network";
        }
        try {
            log.debug("Resolving location for IP: {}", ip);
            String url = "http://ip-api.com/json/" + ip + "?fields=status,country,regionName,city";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("\"success\"")) {
                String city    = extractJsonField(resp.body(), "city");
                String region  = extractJsonField(resp.body(), "regionName");
                String country = extractJsonField(resp.body(), "country");
                String location = Stream.of(city, region, country)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(", "));
                String result = location.isBlank() ? "Unknown" : location;
                log.debug("Location resolved for IP: {} -> {}", ip, result);
                return result;
            }
        } catch (Exception ex) {
            log.warn("Geo lookup failed for ip={}: {}", ip, ex.getMessage());
        }
        return "Unknown";
    }

    /**
     * Builds a stable fingerprint for the device:
     * - Uses client-provided deviceId if present (reliable for mobile/native apps)
     * - Falls back to a hash of UA+OS+Browser for web browsers
     */
    private String buildFingerprint(DeviceContext ctx) {
        if (ctx.getDeviceId() != null && !ctx.getDeviceId().isBlank()
                && !"unknown".equalsIgnoreCase(ctx.getDeviceId())) {
            String id = ctx.getDeviceId().trim();
            return id.length() > 64 ? id.substring(0, 64) : id;
        }
        // UA-based fingerprint — stable across sessions for the same browser/OS combo
        String combined = nvl(ctx.getUserAgent()) + nvl(ctx.getOs()) + nvl(ctx.getBrowser());
        return "fp-" + String.format("%08x", combined.hashCode());
    }

    /** Returns true for loopback, link-local, and RFC-1918 private address ranges. */
    private boolean isPrivateIp(String ip) {
        return ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.startsWith("127.")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && isInRange172(ip));
    }

    private boolean isInRange172(String ip) {
        // 172.16.0.0 – 172.31.255.255
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    /** Minimal JSON string-field extractor (avoids ObjectMapper dependency here). */
    private String extractJsonField(String json, String key) {
        String token = "\"" + key + "\":\"";
        int start = json.indexOf(token);
        if (start == -1) return "";
        start += token.length();
        int end = json.indexOf('"', start);
        return end == -1 ? "" : json.substring(start, end);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}

