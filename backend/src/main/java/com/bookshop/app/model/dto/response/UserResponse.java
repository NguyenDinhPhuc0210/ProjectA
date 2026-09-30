package com.bookshop.app.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dùng cho UC01 (register), UC04 (get profile), UC05 (update profile).
 * KHÔNG bao giờ trả: password, reset_token, reset_token_exp, deleted_at, password_changed_at.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private Long userId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private String avatarKey;
    private String avatarUrl;
    private String authProvider;
    private String accountStatus;
    private LocalDateTime createdAt;
}
