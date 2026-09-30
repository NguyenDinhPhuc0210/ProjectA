package com.bookshop.app.controller;

import com.bookshop.app.model.dto.request.ForgotPasswordRequest;
import com.bookshop.app.model.dto.request.LoginRequest;
import com.bookshop.app.model.dto.request.RegisterRequest;
import com.bookshop.app.model.dto.request.ResetPasswordRequest;
import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.UserResponse;
import com.bookshop.app.service.AuthService;
import com.bookshop.app.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — Base URL: /api/v1/auth
 *
 * UC-01: POST /register
 * UC-02: POST /login
 * UC-03: POST /forgot-password & POST /reset-password
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng ký, Đăng nhập, Quên/Đặt lại mật khẩu")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    // ─── UC-01 — Đăng ký tài khoản ───────────────────────────────────────────

    @Operation(summary = "UC-01: Đăng ký tài khoản khách hàng")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse data = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", data));
    }

    // ─── UC-02 — Đăng nhập ───────────────────────────────────────────────────

    @Operation(summary = "UC-02: Đăng nhập hệ thống")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        var data = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", data));
    }

    // ─── UC-03a — Quên mật khẩu ──────────────────────────────────────────────

    @Operation(summary = "UC-03a: Yêu cầu đặt lại mật khẩu (gửi email)")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        authService.forgotPassword(request.getEmail(), clientIp);
        // Luôn trả 200 bất kể email có tồn tại hay không (anti-enumeration)
        return ResponseEntity.ok(
                ApiResponse.success("Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu", null));
    }

    // ─── UC-03b — Đặt lại mật khẩu ──────────────────────────────────────────

    @Operation(summary = "UC-03b: Đặt lại mật khẩu bằng token từ email")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        // Rate limit check theo IP (10 lần / 15 phút)
        rateLimitService.checkResetPasswordLimit(getClientIp(httpRequest));
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(
                ApiResponse.success("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại", null));
    }

    // ─── Util ─────────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
