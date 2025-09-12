package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.entity.User;
import org.springframework.security.authentication.BadCredentialsException;

public interface  AuthService {

    LoginResponse login(LoginRequest request) throws BadCredentialsException;
    LoginResponse issueTokensFor(User user, DeviceContext ctx, boolean recordDeviceMetadata);


}
