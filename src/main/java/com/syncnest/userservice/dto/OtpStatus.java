package com.syncnest.userservice.dto;

public record OtpStatus(
        boolean cooldown,
        boolean resendIntervalLock,
        int used,
        int max,
        int resendIntervalSeconds,
        int cooldownSeconds
) {}
