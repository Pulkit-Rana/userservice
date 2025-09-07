package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.OtpMeta;
import com.syncnest.userservice.dto.RegistrationRequest;
import com.syncnest.userservice.dto.RegistrationResponse;
import com.syncnest.userservice.entity.*;
import com.syncnest.userservice.exception.UserExceptions;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest request) {

        final String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email must be provided.");
        }
        isUserAlreadyRegistered(request.getEmail());

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.ROLE_USER)
                .isLocked(true)
                .isVerified(false)
                .enabled(false)
                .build();
        Profile profile = Profile.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .user(user) // ✅ critical: connect Profile -> User
                .build();
        user.setProfile(profile);
        DeviceMetadata device = DeviceMetadata.builder()
                .user(user)                        // owning side is DeviceMetadata (ManyToOne user)
                .provider(AuthProvider.LOCAL)      // provider lives on DeviceMetadata
                .deviceType(DeviceType.UNKNOWN)    // no device known at registration
                // .location(...)                  // populate if you capture it
                // .lastLoginAt(null)              // not logged in yet
                .build();

        Set<DeviceMetadata> devices = new HashSet<>();
        devices.add(device);
        user.setDevices(devices);
        User saved = userRepository.save(user);

        // 5) Return response
        // 🔑 Construct OTP Meta
        OtpMeta otpMeta = OtpMeta.builder()
                .used(1)
                .max(3)
                .cooldown(false)
                .resendIntervalLock(false)
                .resendIntervalSeconds(60)
                .cooldownSeconds(300)
                .build();

        // ✅ Return with otpMeta
        return RegistrationResponse.builder()
                .success(true)
                .message("User registered successfully. OTP sent to email.")
                .email(saved.getEmail())
                .otpMeta(otpMeta)
                .build();
    }

    @Override
    public User getRegisteredUser(String email) {
        return null;
    }

    public void isUserAlreadyRegistered(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserExceptions.UserAlreadyExists(email);
        }
    }
}
