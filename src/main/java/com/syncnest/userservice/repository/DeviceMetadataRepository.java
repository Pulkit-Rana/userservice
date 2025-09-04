package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceMetadataRepository extends JpaRepository<DeviceMetadata, Long> {
    boolean existsByUserAndDeviceTypeAndLocation(User user, DeviceType deviceType, String location);
    Optional<DeviceMetadata> findFirstByUserAndDeviceTypeAndLocationAndProvider(
            User user, DeviceType deviceType, String location, AuthProvider provider);
}
