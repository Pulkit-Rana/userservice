package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.OtpStatus;
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
     * Verifies the OTP entered by the user against the stored hash.
     * Consumes the OTP on success, starts cooldown on abuse.
     *
     * @return
     * @throws IllegalArgumentException if OTP is invalid, expired, or too many attempts
     */
    LoginResponse verifyAndConsumeOtpOrThrow(VerifyOTPRequest verifyOTP);

    /**
     * Returns the current OTP status for the given email, including cooldown flag,
     * resend interval lock, and number of resends used.
     *
     * @param email user email
     * @return OtpStatus record containing status details
     */
    OtpStatus getOtpStatus(String email);
}
