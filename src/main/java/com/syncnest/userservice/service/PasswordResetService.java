package com.syncnest.userservice.service;

public interface PasswordResetService {

    void sendResetCode(String email, String ipAddress, String userAgent);

    void resetPassword(String email, String resetCode, String newPassword, String ipAddress, String userAgent);
}

