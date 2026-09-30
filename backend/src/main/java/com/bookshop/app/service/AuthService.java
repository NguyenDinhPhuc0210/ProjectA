package com.bookshop.app.service;

import com.bookshop.app.model.dto.request.LoginRequest;
import com.bookshop.app.model.dto.request.RegisterRequest;
import com.bookshop.app.model.dto.response.AuthResponse;
import com.bookshop.app.model.dto.response.UserResponse;

public interface AuthService {

    /** UC-01: Register a new customer account. Returns 201 with user info (no password). */
    UserResponse register(RegisterRequest request);

    /** UC-02: Login with email + password. Returns JWT + user info. */
    AuthResponse login(LoginRequest request, String clientIp);

    /** UC-03 Step 1: Send password reset email (always 200, anti-enumeration). */
    void forgotPassword(String email, String clientIp);

    /** UC-03 Step 2: Reset password using raw UUID token. */
    void resetPassword(String rawToken, String newPassword);
}
