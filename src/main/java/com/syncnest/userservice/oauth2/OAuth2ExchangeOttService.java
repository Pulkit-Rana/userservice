package com.syncnest.userservice.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Short-lived, single-use opaque codes used after browser Google OAuth redirect so the SPA
 * can obtain an access JWT without putting tokens in the browser history (query string).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2ExchangeOttService {

    private static final String KEY_PREFIX = "syn:oauth2:ott:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.oauth2.ott-ttl-seconds:120}")
    private long ottTtlSeconds;

    /**
     * Persists a one-time ticket bound to the given user. Returns the opaque code for the redirect URL.
     */
    public String createForUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        String code = newOpaqueCode();
        String key = KEY_PREFIX + code;
        Duration ttl = Duration.ofSeconds(Math.max(30, Math.min(ottTtlSeconds, 600)));
        stringRedisTemplate.opsForValue().set(key, userId.toString(), ttl);
        log.debug("Issued OAuth2 exchange OTT (ttl={}s)", ttl.toSeconds());
        return code;
    }

    /**
     * Atomically consumes the code and returns the user id, or empty if unknown/expired/already used.
     */
    public UUID consume(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.length() > 128) {
            return null;
        }
        String key = KEY_PREFIX + trimmed;
        String userIdStr = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(userIdStr)) {
            return null;
        }
        try {
            return UUID.fromString(userIdStr.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid user id in OAuth2 OTT payload");
            return null;
        }
    }

    private String newOpaqueCode() {
        byte[] buf = new byte[32];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
