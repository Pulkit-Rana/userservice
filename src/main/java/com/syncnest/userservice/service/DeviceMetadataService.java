package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.entity.User;

public interface DeviceMetadataService {

    /**
     * Upserts a device record for the given user based on the enriched DeviceContext.
     * If a matching device already exists (by deviceId), updates IP / location / last-login.
     * Otherwise inserts a new device record.
     * This call is async and best-effort — failures are swallowed so auth flows are never blocked.
     */
    void upsertDeviceLogin(User user, DeviceContext ctx);
}

