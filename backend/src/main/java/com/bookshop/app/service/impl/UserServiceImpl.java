package com.bookshop.app.service.impl;

import com.bookshop.app.config.AvatarConfig;
import com.bookshop.app.exception.BusinessException;
import com.bookshop.app.exception.DuplicateResourceException;
import com.bookshop.app.exception.ResourceNotFoundException;
import com.bookshop.app.model.dto.request.ChangePasswordRequest;
import com.bookshop.app.model.dto.request.UpdateProfileRequest;
import com.bookshop.app.model.dto.response.AvatarPresetResponse;
import com.bookshop.app.model.dto.response.UserResponse;
import com.bookshop.app.model.entity.User;
import com.bookshop.app.model.enums.AuthProvider;
import com.bookshop.app.repository.UserRepository;
import com.bookshop.app.security.JwtUtil;
import com.bookshop.app.service.UserService;
import com.bookshop.app.util.InputNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final AvatarConfig    avatarConfig;
    private final JwtUtil         jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // ─── UC-04: Xem thông tin cá nhân ────────────────────────────────────────

    @Override
    public UserResponse getMyProfile(Long userId) {
        User user = findActiveUserById(userId);
        return toUserResponse(user);
    }

    // ─── UC-05: Cập nhật thông tin cá nhân (partial PATCH) ───────────────────

    @Override
    @Transactional
    public UserResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUserById(userId);

        // ── fullName ─────────────────────────────────────────────────────────
        // "không gửi" → skip; "gửi null hoặc blank" → 400
        if (request.isFullNamePresent()) {
            String fullName = request.getFullName();
            if (fullName == null || fullName.isBlank()) {
                throw new BusinessException("Họ tên phải từ 2 đến 100 ký tự");
            }
            fullName = InputNormalizer.normalizeName(fullName);
            if (fullName.length() < 2 || fullName.length() > 100) {
                throw new BusinessException("Họ tên phải từ 2 đến 100 ký tự");
            }
            user.setFullName(fullName);
        }

        // ── phoneNumber ──────────────────────────────────────────────────────
        // "không gửi" → skip; "gửi null" → 400; format sai → 400; trùng user khác → 409
        if (request.isPhoneNumberPresent()) {
            String phone = request.getPhoneNumber();
            if (phone == null) {
                throw new BusinessException("Số điện thoại không hợp lệ");
            }
            phone = InputNormalizer.normalizePhone(phone);
            if (!InputNormalizer.isValidPhone(phone)) {
                throw new BusinessException("Số điện thoại không hợp lệ");
            }
            // Kiểm tra trùng — loại trừ chính user hiện tại
            if (userRepository.existsByPhoneNumberAndNotDeletedExcludingId(phone, userId)) {
                throw new DuplicateResourceException("phoneNumber", "Số điện thoại này đã được sử dụng");
            }
            user.setPhoneNumber(phone);
        }

        // ── avatarKey ────────────────────────────────────────────────────────
        // "không gửi" → skip; "gửi null" → đặt lại "default"; không trong danh sách → 400
        if (request.isAvatarKeyPresent()) {
            String avatarKey = request.getAvatarKey();
            if (avatarKey == null) {
                // Spec: avatarKey = null → reset về default
                user.setAvatarKey("default");
            } else {
                if (!avatarConfig.isValidKey(avatarKey)) {
                    throw new BusinessException("Ảnh đại diện không hợp lệ");
                }
                user.setAvatarKey(avatarKey);
            }
        }

        // ── Lưu — bắt race condition trùng phone ────────────────────────────
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("uq_users_phone_number")) {
                throw new DuplicateResourceException("phoneNumber", "Số điện thoại này đã được sử dụng");
            }
            throw ex;
        }

        log.info("Profile updated: userId={}", userId);
        return toUserResponse(user);
    }

    // ─── UC-06: Đổi mật khẩu ─────────────────────────────────────────────────

    @Override
    @Transactional
    public String changePassword(Long userId, ChangePasswordRequest request) {
        User user = findActiveUserById(userId);

        // 1. Chỉ LOCAL account mới có thể đổi mật khẩu
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new BusinessException("Tài khoản đăng nhập bằng Google không có mật khẩu để đổi");
        }

        String currentPassword = request.getCurrentPassword();
        String newPassword     = request.getNewPassword();

        // 2. Kiểm tra currentPassword đúng không
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("Mật khẩu hiện tại không chính xác");
        }

        // 3. Mật khẩu mới không được trùng mật khẩu cũ
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("Mật khẩu mới không được trùng mật khẩu cũ");
        }

        // 4. Validate độ mạnh mật khẩu mới
        if (!InputNormalizer.isValidPassword(newPassword)) {
            throw new BusinessException(
                "Mật khẩu phải từ 8 đến 64 ký tự, gồm chữ hoa, số và ký tự đặc biệt");
        }

        // 5. Hash và lưu mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));

        // 6. Cập nhật password_changed_at → vô hiệu hoá mọi JWT cũ (trên thiết bị khác)
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // 7. Cấp JWT mới để phiên hiện tại KHÔNG bị đăng xuất
        String newToken = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        log.info("Password changed: userId={}", userId);
        return newToken;
    }

    // ─── UC-08: Danh sách avatar có sẵn ──────────────────────────────────────

    @Override
    public List<AvatarPresetResponse> listAvatarPresets() {
        return avatarConfig.getPreset();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User findActiveUserById(Long userId) {
        return userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    public UserResponse toUserResponse(User user) {
        String avatarUrl = avatarConfig.getUrlByKey(user.getAvatarKey());
        return UserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .avatarKey(user.getAvatarKey())
                .avatarUrl(avatarUrl)
                .authProvider(user.getAuthProvider().name())
                .accountStatus(user.getAccountStatus().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
