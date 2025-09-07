package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.RegistrationRequest;
import com.syncnest.userservice.dto.RegistrationResponse;
import com.syncnest.userservice.entity.User;

public interface RegistrationService {

    RegistrationResponse registerUser(RegistrationRequest request);
    User getRegisteredUser(String email);
}

