package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceMetadataRepository extends JpaRepository<DeviceMetadata, Long> {

    /** Primary upsert lookup — stable and deterministic even if legacy duplicates exist. */
    Optional<DeviceMetadata> findFirstByUserAndDeviceIdOrderByIdAsc(User user, String deviceId);

    /** All active devices for a user (device management / security overview). */
    List<DeviceMetadata> findAllByUser(User user);

    @Modifying
    @Query("DELETE FROM DeviceMetadata d WHERE d.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
