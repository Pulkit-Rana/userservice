package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.AccountDeletionResponse;
import com.syncnest.userservice.dto.AccountRestorationResponse;
import com.syncnest.userservice.dto.ChangePasswordRequest;
import com.syncnest.userservice.dto.MeProfileResponse;
import com.syncnest.userservice.dto.UpdateProfileRequest;

public interface UserAccountService {

    AccountDeletionResponse softDeleteByEmail(String email, String ipAddress, String userAgent);

    AccountRestorationResponse restoreByEmail(String email, String password, String ipAddress, String userAgent);

    int purgeExpiredSoftDeletes();

    MeProfileResponse getCurrentProfile(String email);

    MeProfileResponse updateCurrentProfile(String email, UpdateProfileRequest request);

    void changePassword(String email, ChangePasswordRequest request);
}


