package com.syncnest.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Keyed HMAC for short-lived numeric codes (signup OTP, password reset).
 * Avoids storing a plain digest of a 6-digit secret (trivial offline search space).
 */
@Component
public class ShortCodeHasher {

    private final SecretKeySpec hmacKey;

    public ShortCodeHasher(
            @Value("${app.security.short-code-hmac-secret:}") String dedicatedSecret,
            @Value("${JWT_SECRET:}") String jwtSecret) {
        String raw = StringUtils.hasText(dedicatedSecret) ? dedicatedSecret : jwtSecret;
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException(
                    "Configure JWT_SECRET or app.security.short-code-hmac-secret (e.g. APP_SHORT_CODE_HMAC_SECRET) for OTP/reset code HMAC.");
        }
        byte[] keyMaterial = sha256(raw.getBytes(StandardCharsets.UTF_8));
        this.hmacKey = new SecretKeySpec(keyMaterial, "HmacSHA256");
    }

    /**
     * Hex-encoded HMAC-SHA256 of the UTF-8 bytes of {@code plaintextCode} (e.g. six digits).
     */
    public String hmacHex(String plaintextCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] out = mac.doFinal(plaintextCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
