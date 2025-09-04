package com.syncnest.userservice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "profiles")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Profile extends BaseEntity{

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Size(max = 100)
    @Column(length = 100)
    private String firstName;

    @Size(max = 100)
    @Column(length = 100)
    private String lastName;

    @Size(max = 15)
    @Column(length = 15)
    private String phoneNumber;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    @Column(length = 100)
    private String city;

    @Size(max = 50)
    @Column(length = 50)
    private String country;

    @Size(max = 10)
    @Column(length = 10)
    private String zipCode;

    private String profilePictureUrl;

}
