package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.OtpStatus;
import com.syncnest.userservice.dto.VerifyOTPRequest;
import com.syncnest.userservice.service.OtpService;
import com.syncnest.userservice.utils.EmailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.token.Sha512DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailTemplate emailService;

    // ==== Config ====
    private static final String OTP_PREFIX = "otp:signup:";
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(5);

    private static final int MAX_RESENDS = 4;                  // total sends allowed (after the first, or total including first? Here: total including first send)
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // ==== Keys ====
    private String keyOtp(String email)            { return OTP_PREFIX + email + ":code"; }
    private String keyResendCount(String email)    { return OTP_PREFIX + email + ":resends"; }
    private String keyResendLock(String email)     { return OTP_PREFIX + email + ":resend_lock"; }   // 1-min lock
    private String keyCooldown(String email)       { return OTP_PREFIX + email + ":cooldown"; }      // 5-min cooldown
    private String keyAttempts(String email)       { return OTP_PREFIX + email + ":attempts"; }      // wrong attempts

    private String norm(String email) {
        if (!StringUtils.hasText(email)) throw new IllegalArgumentException("Email must be provided.");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ==== Public API ====

    /**
     * Generates a 6-digit OTP, stores its hash with TTL, and emails the user.
     * Enforces: cooldown, 1-min resend interval, and max 4 sends.
     */
    @Override
    public void generateAndSendOtp(String rawEmail) {
        final String email = norm(rawEmail);

        enforceNotInCooldown(email);
        enforceResendInterval(email);

        int current = incrementResendCount(email);
        if (current > MAX_RESENDS) {
            startCooldown(email);
            throw new IllegalArgumentException("Too many OTP requests. Please try again after 5 minutes.");
        }

        // Generate OTP (000000–999999, but avoid leading zeros confusion → 100000–999999)
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));

        // Hash & store with TTL
        String hashedOtp = Sha512DigestUtils.shaHex(otp);
        redisTemplate.opsForValue().set(keyOtp(email), hashedOtp, OTP_EXPIRY);

        // Reset wrong attempts counter with the same TTL
        redisTemplate.opsForValue().set(keyAttempts(email), "0", OTP_EXPIRY);

        // Set resend lock for 1 minute (prevent spamming)
        redisTemplate.opsForValue().set(keyResendLock(email), "1", RESEND_INTERVAL);

        // Send the OTP
        emailService.sendOtp(email, otp);
    }

    /**
     * Verifies the OTP and consumes it on success.
     * Enforces: max wrong attempts; starts cooldown on too many failures.
     *
     * @return
     */
    @Override
    public LoginResponse verifyAndConsumeOtpOrThrow(VerifyOTPRequest verifyOTP) {
        final String email = norm(verifyOTP.getEmail());

        String storedHash = redisTemplate.opsForValue().get(keyOtp(email));
        if (storedHash == null) {
            throw new IllegalArgumentException("OTP expired or invalid.");
        }

        String hashedEnteredOtp = Sha512DigestUtils.shaHex(verifyOTP.getOtp());

        if (!hashedEnteredOtp.equals(storedHash)) {
            int attempts = incrementAttempts(email);
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                // burn the OTP and start cooldown to throttle brute force
                deleteOtpKeys(email);
                startCooldown(email);
                throw new IllegalArgumentException("Too many incorrect attempts. Please request a new OTP after 5 minutes.");
            }
            throw new IllegalArgumentException("Incorrect OTP.");
        }

        // Success → consume OTP & counters
        deleteOtpKeys(email);
        return null;
    }

    // ==== Helpers ====

    private void enforceNotInCooldown(String email) {
        if (redisTemplate.hasKey(keyCooldown(email))) {
            throw new IllegalArgumentException("Please wait 5 minutes before requesting another OTP.");
        }
    }

    private void enforceResendInterval(String email) {
        if (redisTemplate.hasKey(keyResendLock(email))) {
            throw new IllegalArgumentException("Please wait 1 minute before requesting another OTP.");
        }
    }

    private int incrementResendCount(String email) {
        String key = keyResendCount(email);
        Long val = redisTemplate.opsForValue().increment(key);
        // Ensure the resend counter has a sensible TTL (tie it to the OTP flow window)
        if (val != null && val == 1L) {
            // First time we see this counter: set TTL (e.g., 30 minutes window; adjust as needed)
            redisTemplate.expire(key, Duration.ofMinutes(30));
        }
        return Optional.ofNullable(val).map(Long::intValue).orElse(1);
    }

    private int incrementAttempts(String email) {
        String key = keyAttempts(email);
        Long val = redisTemplate.opsForValue().increment(key);
        // keep attempts TTL aligned with current OTP TTL
        if (val != null && val == 1L) {
            redisTemplate.expire(key, OTP_EXPIRY);
        }
        return Optional.ofNullable(val).map(Long::intValue).orElse(1);
    }

    private void startCooldown(String email) {
        redisTemplate.opsForValue().set(keyCooldown(email), "1", COOLDOWN);
    }

    private void deleteOtpKeys(String email) {
        redisTemplate.delete(keyOtp(email));
        redisTemplate.delete(keyAttempts(email));
        redisTemplate.delete(keyResendLock(email));
        // Intentionally keep resend count so users can't game the quota in one window.
        // Cooldown is set only on abuse/too many attempts or quota exceeded.
    }

    // (Optional) expose a check if the user can request an OTP now; useful for UI
    @Override
    public OtpStatus getOtpStatus(String rawEmail) {
        final String email = norm(rawEmail);
        boolean inCooldown = redisTemplate.hasKey(keyCooldown(email));
        boolean lockedForResendInterval = redisTemplate.hasKey(keyResendLock(email));
        int resends = Optional.ofNullable(redisTemplate.opsForValue().get(keyResendCount(email)))
                .map(Integer::parseInt)
                .orElse(0);

        return new OtpStatus(
                inCooldown,
                lockedForResendInterval,
                resends,
                MAX_RESENDS,
                (int) RESEND_INTERVAL.toSeconds(),
                (int) COOLDOWN.toSeconds()
        );
    }
}
