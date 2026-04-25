package com.syncnest.userservice.dto;

public record OtpStatus(
        boolean cooldown,
        boolean resendIntervalLock,
        int used,
        int max,
        int resendIntervalSeconds,
        int cooldownSeconds,
        /** Seconds left until the current OTP expires (0 if none). */
        int otpSecondsRemaining,
        /** Seconds left on the resend rate-limit lock (0 if none). */
        int resendLockSecondsRemaining
) {}
