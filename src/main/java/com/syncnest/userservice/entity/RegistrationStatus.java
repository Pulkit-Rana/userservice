package com.syncnest.userservice.entity;

/**
 * Tracks the registration lifecycle of a user.
 */
public enum RegistrationStatus {

    /** Registration initiated — user record created, OTP not yet sent. */
    INITIATED,

    /** OTP has been sent and is pending verification. */
    OTP_PENDING,

    /** OTP verified — account activated. */
    OTP_VERIFIED,

    /** Registration fully complete (verified + first login). */
    COMPLETE
}

