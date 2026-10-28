package com.offerget.service;

import com.offerget.dto.auth.AuthResponse;
import com.offerget.dto.auth.LoginRequest;
import com.offerget.dto.auth.RegisterRequest;
import com.offerget.dto.auth.UserVO;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserVO currentUser();

}
