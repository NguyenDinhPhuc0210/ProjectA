package com.bookshop.app.model.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;

/**
 * UC-05: Partial update request.
 *
 * Phân biệt "field không có trong JSON" vs "field gửi null":
 *   - Field vắng mặt  → xxxPresent = false → giữ nguyên giá trị cũ
 *   - Field gửi null  → xxxPresent = true, value = null → validate → 400 (fullName/phone)
 *                                                       → reset "default" (avatarKey)
 *
 * Dùng @JsonSetter trên setter để bật flag khi Jackson ghi giá trị.
 */
@Getter
public class UpdateProfileRequest {

    // ─── fullName ─────────────────────────────────────────────────────────────

    private String fullName;
    private boolean fullNamePresent = false;

    @JsonSetter("fullName")
    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.fullNamePresent = true;
    }

    // ─── phoneNumber ──────────────────────────────────────────────────────────

    private String phoneNumber;
    private boolean phoneNumberPresent = false;

    @JsonSetter("phoneNumber")
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.phoneNumberPresent = true;
    }

    // ─── avatarKey ────────────────────────────────────────────────────────────
    // null → reset về "default" (spec: avatarKey = null → đặt lại default)

    private String avatarKey;
    private boolean avatarKeyPresent = false;

    @JsonSetter("avatarKey")
    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
        this.avatarKeyPresent = true;
    }
}
