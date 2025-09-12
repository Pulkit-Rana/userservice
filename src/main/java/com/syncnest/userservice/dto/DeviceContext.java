package com.syncnest.userservice.dto;

import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeviceContext {
    String deviceId;       // normalized per policy
    String clientId;       // optional; used for fallback normalization
    String location;       // optional
    AuthProvider provider; // default LOCAL
    DeviceType deviceType; // default UNKNOWN
}