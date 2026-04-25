package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.UserSummary;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserSummaryMapperImpl implements UserSummaryMapper {

    private final UserRepository userRepository;

    @Override
    public UserSummary toSummary(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user and id are required");
        }
        UUID id = user.getId();
        User u = userRepository.findById(id).orElse(user);

        Profile p = u.getProfile();
        String displayName = null;
        String profilePictureUrl = null;
        if (p != null) {
            if (StringUtils.hasText(p.getFirstName()) || StringUtils.hasText(p.getLastName())) {
                String fn = p.getFirstName() != null ? p.getFirstName().trim() : "";
                String ln = p.getLastName() != null ? p.getLastName().trim() : "";
                displayName = (fn + " " + ln).trim();
            }
            if (StringUtils.hasText(p.getProfilePictureUrl())) {
                profilePictureUrl = p.getProfilePictureUrl();
            }
        }
        if (!StringUtils.hasText(displayName) && StringUtils.hasText(u.getEmail())) {
            int at = u.getEmail().indexOf('@');
            displayName = at > 0 ? u.getEmail().substring(0, at) : u.getEmail();
        }

        return UserSummary.builder()
                .id(u.getId().toString())
                .email(u.getEmail())
                .displayName(displayName)
                .profilePictureUrl(profilePictureUrl)
                .roles(Set.of(u.getRole().getValue()))
                .emailVerified(u.isVerified())
                .googleLinked(StringUtils.hasText(u.getGoogleSub()))
                .build();
    }
}
