package com.bookshop.app.controller;

import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.AvatarPresetResponse;
import com.bookshop.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AvatarController — Public endpoint (không cần auth).
 * UC-08: GET /api/v1/avatars — Danh sách avatar có sẵn.
 */
@RestController
@RequestMapping("/api/v1/avatars")
@RequiredArgsConstructor
@Tag(name = "Avatar", description = "Danh sách avatar có sẵn (public)")
public class AvatarController {

    private final UserService userService;

    @Operation(summary = "UC-08: Lấy danh sách avatar có sẵn (không cần đăng nhập)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AvatarPresetResponse>>> listAvatarPresets() {
        List<AvatarPresetResponse> data = userService.listAvatarPresets();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách avatar thành công", data));
    }
}
