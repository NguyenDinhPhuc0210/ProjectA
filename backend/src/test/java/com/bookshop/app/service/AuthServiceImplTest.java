package com.bookshop.app.service;

import com.bookshop.app.exception.BusinessException;
import com.bookshop.app.exception.DuplicateResourceException;
import com.bookshop.app.model.dto.request.LoginRequest;
import com.bookshop.app.model.dto.request.RegisterRequest;
import com.bookshop.app.repository.UserRepository;
import com.bookshop.app.security.JwtUtil;
import com.bookshop.app.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl — Sprint 1.
 * Covers: register (happy path + duplicate), all login branches, expired/reused reset token.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private RateLimitService rateLimitService;

    @InjectMocks
    private AuthServiceImpl authService;

    // ─── UC-01: Register ──────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-01: Register success → returns user info, no password")
    void register_success() {
        // TODO: implement after AuthServiceImpl is done
    }

    @Test
    @DisplayName("UC-01: Duplicate email → 409 DuplicateResourceException")
    void register_duplicateEmail_throws409() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-01: Duplicate phone → 409 DuplicateResourceException")
    void register_duplicatePhone_throws409() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-01: Invalid password policy → throws BusinessException")
    void register_invalidPassword_throwsBusinessException() {
        // TODO: implement
    }

    // ─── UC-02: Login ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02: Login success → returns JWT + user info")
    void login_success() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-02: Non-existent email → 401 (anti-enumeration, same message)")
    void login_emailNotFound_returns401() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-02: Wrong password → 401 (same generic message)")
    void login_wrongPassword_returns401() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-02: GOOGLE account → 400 (dedicated message)")
    void login_googleAccount_returns400() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-02: LOCKED account (correct password) → 403")
    void login_lockedAccount_returns403() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-02: Rate limit exceeded → 429")
    void login_rateLimitExceeded_returns429() {
        // TODO: implement
    }

    // ─── UC-03: Reset Password ────────────────────────────────────────────────

    @Test
    @DisplayName("UC-03 Step2: Expired token → 400 BusinessException")
    void resetPassword_expiredToken_throws400() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-03 Step2: Token already used (null) → 400 BusinessException")
    void resetPassword_reusedToken_throws400() {
        // TODO: implement
    }

    @Test
    @DisplayName("UC-03 Step2: Valid token → password updated, passwordChangedAt set")
    void resetPassword_success_updatesPasswordChangedAt() {
        // TODO: implement
    }
}
