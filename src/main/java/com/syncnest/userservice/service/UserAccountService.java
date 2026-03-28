package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.AccountDeletionResponse;
import com.syncnest.userservice.dto.AccountRestorationResponse;

public interface UserAccountService {

    AccountDeletionResponse softDeleteByEmail(String email, String ipAddress, String userAgent);

    AccountRestorationResponse restoreByEmail(String email, String password, String ipAddress, String userAgent);

    int purgeExpiredSoftDeletes();
}

