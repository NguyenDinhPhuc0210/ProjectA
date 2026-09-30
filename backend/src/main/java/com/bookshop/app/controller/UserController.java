package com.bookshop.app.controller;

import com.bookshop.app.model.dto.request.ChangePasswordRequest;
import com.bookshop.app.model.dto.request.UpdateProfileRequest;
import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.AuthResponse;
import com.bookshop.app.model.dto.response.UserResponse;
import com.bookshop.app.security.JwtUtil;
import com.bookshop.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * UserController — Base URL: /api/v1/users
 *
 * UC-04: GET  /me        — Xem thông tin cá nhân
 * UC-05: PATCH /me       — Cập nhật thông tin cá nhân
 * UC-06: PATCH /me/password — Đổi mật khẩu
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "Thông tin cá nhân và đổi mật khẩu")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final JwtUtil     jwtUtil;

    // ─── UC-04 — Xem thông tin cá nhân ───────────────────────────────────────

    @Operation(summary = "UC-04: Xem thông tin cá nhân")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId) {

        UserResponse data = userService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin thành công", data));
    }

    // ─── UC-05 — Cập nhật thông tin cá nhân (partial) ────────────────────────

    @Operation(summary = "UC-05: Cập nhật thông tin cá nhân (partial update)")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse data = userService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", data));
    }

    // ─── UC-06 — Đổi mật khẩu ────────────────────────────────────────────────

    @Operation(summary = "UC-06: Đổi mật khẩu (chỉ tài khoản LOCAL)")
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<?>> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {

        String newToken = userService.changePassword(userId, request);
        // Spec UC-06: trả AuthResponse với token mới — token cũ bị vô hiệu qua password_changed_at
        AuthResponse authData = AuthResponse.builder()
                .accessToken(newToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationSeconds())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", authData));
    }
}
