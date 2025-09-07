package com.syncnest.userservice.utils;

import com.syncnest.userservice.entity.DeviceType;
import jakarta.servlet.http.HttpServletRequest;

public class DeviceMetaData {

    private String getClientIP(HttpServletRequest request) {
         String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(request.getRemoteAddr())) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    public static DeviceType getDeviceType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return DeviceType.UNKNOWN;
        }
        if (userAgent.toLowerCase().contains("mobile")) {
            return DeviceType.MOBILE;
        } else if (userAgent.toLowerCase().contains("windows")
                || userAgent.toLowerCase().contains("macintosh")) {
            return DeviceType.DESKTOP;
        }
        return DeviceType.UNKNOWN;
    }
}
