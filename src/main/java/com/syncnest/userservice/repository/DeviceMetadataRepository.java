package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceMetadataRepository extends JpaRepository<DeviceMetadata, Long> {

    /** Primary upsert lookup — find device by stable fingerprint. */
    Optional<DeviceMetadata> findByUserAndDeviceId(User user, String deviceId);

    /** All active devices for a user (device management / security overview). */
    List<DeviceMetadata> findAllByUser(User user);
}
