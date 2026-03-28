package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.GoogleLoginRequest;
import com.syncnest.userservice.dto.LoginResponse;

public interface GoogleAuthService {

    LoginResponse loginWithGoogle(GoogleLoginRequest request, DeviceContext context);
}

