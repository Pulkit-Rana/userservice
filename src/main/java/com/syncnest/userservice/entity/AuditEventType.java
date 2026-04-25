package com.syncnest.userservice.entity;

public enum AuditEventType {
    LOGIN,
    REGISTRATION,
    OTP_VERIFICATION,
    REFRESH_TOKEN,
    TOKEN_REPLAY_DETECTED,
    LOGOUT,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET,
    /** User changed password while authenticated (sessions may be revoked). */
    PASSWORD_CHANGE,
    USER_SOFT_DELETE,
    USER_RESTORE,
    USER_HARD_DELETE,
    PROFILE_UPDATE
}

