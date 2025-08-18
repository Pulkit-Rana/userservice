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

    private static final String KEY_PREFIX = "jwt:bl:";   // blacklist namespace
    private static final long   SKEW_MS    = 5_000L;      // small safety skew

    private final StringRedisTemplate redis;              // faster for String values
    private final JwtTokenProviderConfig jwtService;      // our provider (with extractClaim)

    /**
     * Blacklist a token until its expiration. Uses jti when present (preferred),
     * otherwise falls back to SHA-256(token).
     */
    public void addToBlacklist(@NonNull String token) {
        try {
            // Skip obviously bad tokens fast
            if (token.isBlank() || jwtService.isTokenExpired(token)) return;

            // TTL based on token's exp
            Date exp = jwtService.extractExpiration(token);
            if (exp == null) return; // defensive
            long ttlMs = (exp.getTime() - System.currentTimeMillis()) - SKEW_MS;
            if (ttlMs <= 0) return;

            // Prefer JTI; fallback to hash
            String jti = safe(jwtService.extractClaim(token, Claims::getId));
            String key = KEY_PREFIX + (jti != null ? ("jti:" + jti) : ("sha:" + sha256Url(token)));

            // Store a tiny value; presence of key is what matters
            redis.opsForValue().set(key, "1", ttlMs, TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException e) {
            // Invalid token (parse failed) — ignore
            log.debug("Blacklist skip: invalid token: {}", e.getMessage());
        } catch (DataAccessException dae) {
            // Redis issue — fail open (don't break request flow)
            log.warn("Redis unavailable while blacklisting token: {}", dae.getMessage());
        } catch (Exception ex) {
            log.warn("Unexpected error while blacklisting token: {}", ex.getMessage());
        }
    }

    /**
     * Check if a token (by JTI or hash fallback) is blacklisted.
     * Returns false on Redis errors to avoid blocking valid traffic.
     */
    public boolean isBlacklisted(@NonNull String token) {
        try {
            if (token.isBlank()) return false;

            // Try JTI first
            Optional<String> jtiOpt = safeOpt(() -> jwtService.extractClaim(token, Claims::getId));
            return jtiOpt.map(s -> redisExists(KEY_PREFIX + "jti:" + s)).orElseGet(() -> redisExists(KEY_PREFIX + "sha:" + sha256Url(token)));

            // Fallback: hash the token

        } catch (IllegalArgumentException e) {
            // Invalid token (parse failed) — treat as not blacklisted
            log.debug("Blacklist check: invalid token: {}", e.getMessage());
            return false;
        } catch (DataAccessException dae) {
            log.warn("Redis unavailable during blacklist check: {}", dae.getMessage());
            return false; // fail-open; JWT validation still protects you
        } catch (Exception ex) {
            log.warn("Unexpected error during blacklist check: {}", ex.getMessage());
            return false;
        }
    }

    /** Safe wrapper that avoids nullable Boolean unboxing warnings. */
    private boolean redisExists(String key) {
        return redis.hasKey(key);
    }

    // ---------- helpers ----------

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

    private String sha256Url(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(token.getBytes(StandardCharsets.UTF_8));
            // URL-safe Base64 (no padding) to keep keys compact
            return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
        } catch (Exception e) {
            // Should not happen; fallback to hex of the token bytes
            return bytesToHex(token.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte value : b) sb.append(String.format("%02x", value));
        return sb.toString();
    }

    @FunctionalInterface
    private interface UnsafeSupplier<T> {
        T get() throws Exception;
    }
}
