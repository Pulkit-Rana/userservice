package com.syncnest.userservice.validation;

/**
 * Single source of truth for API validation limits aligned with JPA column sizes.
 */
public final class FieldConstraints {

    private FieldConstraints() {}

    /** Matches {@link com.syncnest.userservice.entity.User#email} column length. */
    public static final int USER_EMAIL_MAX = 50;

    /** Matches {@link com.syncnest.userservice.entity.DeviceMetadata#userAgent}. */
    public static final int DEVICE_USER_AGENT_MAX = 512;

    /** Matches {@link com.syncnest.userservice.entity.DeviceMetadata#location}. */
    public static final int DEVICE_LOCATION_MAX = 255;

    /** Matches {@link com.syncnest.userservice.entity.DeviceMetadata#deviceId} / refresh session id. */
    public static final int DEVICE_ID_MAX = 64;

    /** Matches profile first/last name columns. */
    public static final int PROFILE_NAME_MAX = 100;

    /** Typical VARCHAR password column cap; bcrypt hashes are shorter. */
    public static final int PASSWORD_MAX = 255;

    /** Raw refresh token from client (Base64 etc.); generous cap to avoid log/DoS abuse. */
    public static final int REFRESH_TOKEN_RAW_MAX = 2048;
}
