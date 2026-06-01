package com.example.order.service;

import com.example.order.dto.request.LoginRequest;
import com.example.order.dto.request.RegisterRequest;
import com.example.order.dto.response.AuthResponse;
import com.example.order.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
