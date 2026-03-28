package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.RegistrationRequest;
import com.syncnest.userservice.dto.RegistrationResponse;

public interface RegistrationService {

    RegistrationResponse registerUser(RegistrationRequest request);
}

