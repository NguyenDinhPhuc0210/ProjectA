package com.bookshop.app.model.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * UC-02 Login response.
 * Format:
 * {
 *   "accessToken": "eyJ...",
 *   "tokenType":   "Bearer",
 *   "expiresIn":   86400,
 *   "user": { ... }
 * }
 */
@Data
@Builder
public class AuthResponse {
    private String      accessToken;
    private String      tokenType;
    private long        expiresIn;   // seconds (86400 = 24h)
    private UserResponse user;
}
