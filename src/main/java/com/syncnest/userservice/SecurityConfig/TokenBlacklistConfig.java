package com.syncnest.userservice.SecurityConfig;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistConfig {

    private static final String KEY_PREFIX = "jwt:bl:";     // blacklist namespace
    private static final long   SKEW_MS    = 5_000L;        // safety skew
    private static final long   MIN_TTL_MS = 100L;          // clamp very short TTLs

    private final StringRedisTemplate redis;
    private final JwtTokenProviderConfig jwtService;

    /** Blacklist a token until its expiration. Prefer JTI; fallback to SHA-256(token). */
    public void addToBlacklist(@NonNull String token) {
        try {
            if (token.isBlank()) return;

            // Parse once; avoid double work
            Date exp = jwtService.extractExpiration(token);
            if (exp == null) return;

            long ttlMs = (exp.getTime() - System.currentTimeMillis()) - SKEW_MS;
            if (ttlMs <= 0) return;
            if (ttlMs < MIN_TTL_MS) ttlMs = MIN_TTL_MS;

            String jti = safe(jwtService.extractClaim(token, Claims::getId));
            String iss = safe(jwtService.extractClaim(token, Claims::getIssuer));

            String key = buildKey(jti, iss, token);
            // Presence is what matters; value is tiny
            redis.opsForValue().set(key, "1", ttlMs, TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException e) {
            // Invalid token (parse failed) — ignore
            log.debug("Blacklist skip: invalid token", e);
        } catch (DataAccessException dae) {
            // Redis issue — fail open (don't break request flow)
            log.warn("Redis unavailable while blacklisting token", dae);
        } catch (Exception ex) {
            log.warn("Unexpected error while blacklisting token", ex);
        }
    }

    /** Overload: blacklist by jti/exp/iss (use when you minted the token and have claims). */
    public void addToBlacklist(@NonNull String jti, Date exp, String iss) {
        try {
            if (exp == null) return;
            long ttlMs = (exp.getTime() - System.currentTimeMillis()) - SKEW_MS;
            if (ttlMs <= 0) return;
            if (ttlMs < MIN_TTL_MS) ttlMs = MIN_TTL_MS;

            String key = KEY_PREFIX + (iss != null ? "iss:" + sha256Url(iss) + ":" : "") + "jti:" + jti;
            redis.opsForValue().set(key, "1", ttlMs, TimeUnit.MILLISECONDS);
        } catch (DataAccessException dae) {
            log.warn("Redis unavailable while blacklisting token (jti overload)", dae);
        } catch (Exception ex) {
            log.warn("Unexpected error while blacklisting token (jti overload)", ex);
        }
    }

    /** Check if a token (by JTI or hash fallback) is blacklisted. Fail-open on Redis errors. */
    public boolean isBlacklisted(@NonNull String token) {
        try {
            if (token.isBlank()) return false;

            Optional<String> jtiOpt = safeOpt(() -> jwtService.extractClaim(token, Claims::getId));
            Optional<String> issOpt = safeOpt(() -> jwtService.extractClaim(token, Claims::getIssuer));

            String key = jtiOpt
                    .map(jti -> KEY_PREFIX + issPrefix(issOpt.orElse(null)) + "jti:" + jti)
                    .orElseGet(() -> KEY_PREFIX + issPrefix(issOpt.orElse(null)) + "sha:" + sha256Url(token));

            return redisExists(key);
        } catch (IllegalArgumentException e) {
            // Invalid token (parse failed) — treat as not blacklisted
            log.debug("Blacklist check: invalid token", e);
            return false;
        } catch (DataAccessException dae) {
            log.warn("Redis unavailable during blacklist check", dae);
            return false; // fail-open; signature/exp validation still applies
        } catch (Exception ex) {
            log.warn("Unexpected error during blacklist check", ex);
            return false;
        }
    }

    /** Optional: per-subject revocation fence for "logout all sessions". */
    public void setRevocationFence(@NonNull String issuer, @NonNull String subject, long revokedAfterEpochSeconds, long ttlSeconds) {
        try {
            String key = KEY_PREFIX + "iss:" + sha256Url(issuer) + ":sub:" + sha256Url(subject) + ":revoked-after";
            redis.opsForValue().set(key, Long.toString(revokedAfterEpochSeconds), ttlSeconds, TimeUnit.SECONDS);
        } catch (DataAccessException dae) {
            log.warn("Redis unavailable while setting revocation fence", dae);
        } catch (Exception ex) {
            log.warn("Unexpected error while setting revocation fence", ex);
        }
    }

    /** Check helper to compare token iat against fence (call from your auth filter). */
    public boolean isBeforeFence(String issuer, String subject, long iatEpochSeconds) {
        try {
            String key = KEY_PREFIX + "iss:" + sha256Url(issuer) + ":sub:" + sha256Url(subject) + ":revoked-after";
            String v = redis.opsForValue().get(key);
            if (v == null) return false;
            long fence = Long.parseLong(v);
            return iatEpochSeconds <= fence;
        } catch (Exception e) {
            // On errors, fail-open to avoid auth outages; your JWT checks still run
            log.warn("Error reading revocation fence", e);
            return false;
        }
    }

    // ---------- helpers ----------

    private String buildKey(String jti, String iss, String token) {
        String prefix = KEY_PREFIX + issPrefix(iss);
        if (jti != null) return prefix + "jti:" + jti;
        return prefix + "sha:" + sha256Url(token);
    }

    private String issPrefix(String iss) {
        return (iss != null ? "iss:" + sha256Url(iss) + ":" : "");
    }

    private boolean redisExists(String key) {
        return redis.hasKey(key);
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Optional<String> safeOpt(UnsafeSupplier<String> supplier) {
        try {
            String v = supplier.get();
            return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sha256Url(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
        } catch (Exception e) {
            return bytesToHex(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte value : b) sb.append(String.format("%02x", value));
        return sb.toString();
    }

    @FunctionalInterface
    private interface UnsafeSupplier<T> { T get() throws Exception; }
}
