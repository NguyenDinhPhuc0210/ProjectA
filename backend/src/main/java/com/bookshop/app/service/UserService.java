package com.bookshop.app.service;

import com.bookshop.app.model.dto.request.ChangePasswordRequest;
import com.bookshop.app.model.dto.request.UpdateProfileRequest;
import com.bookshop.app.model.dto.response.AvatarPresetResponse;
import com.bookshop.app.model.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    /** UC-04: Get profile of authenticated user. */
    UserResponse getMyProfile(Long userId);

    /** UC-04: Partial update of profile (fullName, phoneNumber, avatarKey). */
    UserResponse updateMyProfile(Long userId, UpdateProfileRequest request);

    /** UC-04b: Change password (LOCAL accounts only). Returns new JWT. */
    String changePassword(Long userId, ChangePasswordRequest request);

    /** UC-04a: List all avatar presets (no DB call). */
    List<AvatarPresetResponse> listAvatarPresets();
}
