package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.config.CacheConfig;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheConfig.USER_DETAILS_BY_EMAIL,
            keyGenerator = "lowerCaseStringKeyGenerator",
            unless = "#result == null",
            sync = true
    )
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        final String normalized = normalizeEmail(email);
        log.debug("Loading user by email: {}", normalized);

        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Defense-in-depth: block unusable accounts with a generic error (no enumeration)
        if (user.isDeleted() || !user.isVerified() || user.isLocked() || !user.isEnabled()) {
            throw new UsernameNotFoundException("User not found");
        }

        // User implements UserDetails; return as-is
        return user;
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new UsernameNotFoundException("User not found");
        return email.trim().toLowerCase();
    }
}
