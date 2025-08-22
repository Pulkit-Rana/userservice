package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import org.springframework.security.authentication.BadCredentialsException;

public interface  AuthService {

    LoginResponse login(LoginRequest request) throws BadCredentialsException;
}
