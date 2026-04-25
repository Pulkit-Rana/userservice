package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.OtpStatus;
import com.syncnest.userservice.dto.ResendOtpResponse;
import com.syncnest.userservice.dto.VerifyOTPRequest;

public interface OtpService {

    /**
     * Generates a 6-digit OTP for the given email, applies resend/cooldown limits,
     * stores a hashed version in Redis with expiry, and sends it via EmailService.
     *
     * @param email user email
     */
    void generateAndSendOtp(String email);

    /**
     * Resends OTP for a registered-but-unverified user.
     * Validates the user exists and is not yet verified, then delegates to
     * {@link #generateAndSendOtp(String)} which enforces cooldown, rate-limit,
     * and max-resend guards.
     *
     * @param email user email
     * @return response containing success flag, message, and current OTP rate-limit metadata
     */
    ResendOtpResponse resendOtp(String email);

    /**
     * Verifies the OTP entered by the user against the stored hash.
     * Consumes the OTP on success, starts cooldown on abuse.
     *
     * @return login payload including access token and refresh token (refresh is also set HttpOnly by controller)
     * @throws com.syncnest.userservice.exception.OtpExceptions subclasses for invalid / expired / rate-limited OTP
     * @throws com.syncnest.userservice.exception.UserExceptions.UserNotFound if no pending user
     */
    LoginResponse verifyAndConsumeOtpOrThrow(VerifyOTPRequest verifyOTP, DeviceContext deviceContext);

    /**
     * Returns the current OTP status for the given email, including cooldown flag,
     * resend interval lock, and number of resends used.
     *
     * @param email user email
     * @return OtpStatus record containing status details
     */
    OtpStatus getOtpStatus(String email);
}
