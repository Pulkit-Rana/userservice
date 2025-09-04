package com.syncnest.userservice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(
        name = "device_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long meta_id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)

    private AuthProvider provider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 16, nullable = false)
    @Builder.Default
    private DeviceType deviceType = DeviceType.UNKNOWN;

    @Column
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;
}
