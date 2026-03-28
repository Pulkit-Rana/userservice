package com.syncnest.userservice.dto;

import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeviceContext {
    /** Client-supplied device identifier (from request body). */
    String deviceId;
    /** Optional client identifier used as deviceId fallback. */
    String clientId;
    /** Real client IP — always extracted server-side, never trusted from request body. */
    String ip;
    /** Raw User-Agent header from the HTTP request. */
    String userAgent;
    /** Operating system parsed from User-Agent. */
    String os;
    /** Browser name parsed from User-Agent. */
    String browser;
    /** Location resolved from IP (async, best-effort). */
    String location;
    AuthProvider provider; // default LOCAL
    DeviceType deviceType; // default UNKNOWN
}