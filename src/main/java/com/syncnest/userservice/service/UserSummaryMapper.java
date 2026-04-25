package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.UserSummary;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;

import java.util.Set;

/**
 * Builds {@link UserSummary} for API responses, including profile display name and picture when present.
 */
public interface UserSummaryMapper {

    UserSummary toSummary(User user);
}
