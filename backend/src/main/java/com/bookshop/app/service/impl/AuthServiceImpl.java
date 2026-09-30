package com.bookshop.app.service.impl;

import com.bookshop.app.config.AvatarConfig;
import com.bookshop.app.exception.AccountLockedException;
import com.bookshop.app.exception.AuthenticationFailedException;
import com.bookshop.app.exception.BusinessException;
import com.bookshop.app.exception.DuplicateResourceException;
import com.bookshop.app.model.dto.request.LoginRequest;
import com.bookshop.app.model.dto.request.RegisterRequest;
import com.bookshop.app.model.dto.response.AuthResponse;
import com.bookshop.app.model.dto.response.UserResponse;
import com.bookshop.app.model.entity.User;
import com.bookshop.app.model.enums.AccountStatus;
import com.bookshop.app.model.enums.AuthProvider;
import com.bookshop.app.model.enums.Role;
import com.bookshop.app.repository.UserRepository;
import com.bookshop.app.security.JwtUtil;
import com.bookshop.app.service.AuthService;
import com.bookshop.app.service.EmailService;
import com.bookshop.app.service.RateLimitService;
import com.bookshop.app.util.HashUtil;
import com.bookshop.app.util.InputNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository     userRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtUtil            jwtUtil;
    private final EmailService       emailService;
    private final RateLimitService   rateLimitService;
    private final AvatarConfig       avatarConfig;

    // ─── UC-01: Register ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {

        // 1. Chuẩn hoá input
        String email       = InputNormalizer.normalizeEmail(request.getEmail());
        String fullName    = InputNormalizer.normalizeName(request.getFullName());
        String phoneNumber = request.getPhoneNumber() != null
                ? InputNormalizer.normalizePhone(request.getPhoneNumber())
                : null;
        String password    = request.getPassword();

        // 2. Validate password policy
        if (!InputNormalizer.isValidPassword(password)) {
            throw new BusinessException(
                "Mật khẩu phải từ 8 đến 64 ký tự, gồm chữ hoa, số và ký tự đặc biệt");
        }

        // 3. Validate phone format (nếu có gửi)
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            if (!InputNormalizer.isValidPhone(phoneNumber)) {
                throw new BusinessException("Số điện thoại không hợp lệ");
            }
        } else {
            phoneNumber = null; // Chuẩn hoá empty string → null
        }

        // 4. Kiểm tra trùng email (chỉ bản ghi chưa xóa mềm)
        if (userRepository.existsByEmailAndNotDeleted(email)) {
            throw new DuplicateResourceException("email", "Email này đã được sử dụng");
        }

        // 5. Kiểm tra trùng phone (nếu có)
        if (phoneNumber != null && userRepository.existsByPhoneNumberAndNotDeleted(phoneNumber)) {
            throw new DuplicateResourceException("phoneNumber", "Số điện thoại này đã được sử dụng");
        }

        // 6. Hash password
        String hashedPassword = passwordEncoder.encode(password);

        // 7. Tạo entity — anti mass-assignment: role/status LUÔN cố định
        User user = User.builder()
                .email(email)
                .password(hashedPassword)
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .role(Role.ROLE_CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .avatarKey("default")
                .build();

        // 8. Lưu vào DB — bắt race condition
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: 2 request trùng nhau
            String msg = ex.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("uq_users_email")) {
                throw new DuplicateResourceException("email", "Email này đã được sử dụng");
            }
            if (msg != null && msg.contains("uq_users_phone_number")) {
                throw new DuplicateResourceException("phoneNumber", "Số điện thoại này đã được sử dụng");
            }
            throw new DuplicateResourceException("email", "Dữ liệu đã tồn tại");
        }

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());

        // 9. Trả về — không tự động đăng nhập, không trả password
        return toUserResponse(user);
    }

    // ─── UC-02: Login ─────────────────────────────────────────────────────────

    private static final String INVALID_CREDENTIALS_MSG =
            "Thông tin đăng nhập không chính xác";

    /**
     * Dummy BCrypt hash dùng để chạy phép so sánh ngay cả khi email không tồn tại,
     * tránh timing attack (kẻ tấn công đo thời gian response để biết email có tồn tại).
     */
    private static final String DUMMY_HASH =
            "$2a$10$dummyhashfortimingnX2cN4WR4T3Wtu0Fk.pQv8xNvTJQ4K";

    @Override
    public AuthResponse login(LoginRequest request, String clientIp) {

        // 1. Chuẩn hoá email
        String email    = InputNormalizer.normalizeEmail(request.getEmail());
        String password = request.getPassword();

        // 2. Kiểm tra rate limit trước tiên (theo spec: bước đầu tiên)
        rateLimitService.checkLoginLimit(clientIp, email);

        // 3. Tìm user — KHÔNG throw 404 nếu không có, chạy dummy BCrypt để chống timing attack
        var userOpt = userRepository.findByEmailAndNotDeleted(email);

        if (userOpt.isEmpty()) {
            // Chạy dummy compare để thời gian response giống nhau dù email không tồn tại
            passwordEncoder.matches(password, DUMMY_HASH);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MSG);
        }

        User user = userOpt.get();

        // 4. Nếu tài khoản GOOGLE → 400 (thông báo riêng, đây là đánh đổi UX có chủ đích)
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new BusinessException(
                "Tài khoản này đăng ký qua Google. Vui lòng dùng Đăng nhập bằng Google");
        }

        // 5. So sánh password — sai → 401 generic (không lộ lý do cụ thể)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MSG);
        }

        // 6. CHỈ SAU KHI password đúng mới kiểm tra LOCKED
        //    (tránh lộ trạng thái khóa cho kẻ tấn công dò sai password)
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            throw new AccountLockedException(
                "Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ");
        }

        // 7. Tạo JWT
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());

        // 8. Trả về token + user info
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationSeconds())
                .user(toUserResponse(user))
                .build();
    }

    // ─── UC-03 Step 1: Forgot Password ───────────────────────────────────────

    @Override
    public void forgotPassword(String rawEmail, String clientIp) {

        // 1. Kiểm tra rate limit
        rateLimitService.checkForgotPasswordLimit(clientIp, rawEmail);

        // 2. Chuẩn hoá email
        String email = InputNormalizer.normalizeEmail(rawEmail);

        // 3. Luôn trả về 200 — KHÔNG throw exception dù email không tồn tại (anti-enumeration)
        //    Logic bên trong chỉ gửi mail nếu đủ điều kiện, không thì im lặng.
        var userOpt = userRepository.findByEmailAndNotDeleted(email);

        if (userOpt.isEmpty()) {
            // Email không tồn tại → vẫn return void, caller trả 200
            return;
        }

        User user = userOpt.get();

        // 4. Chỉ gửi mail khi: auth_provider = LOCAL và account_status = ACTIVE
        //    GOOGLE hoặc LOCKED → vẫn trả 200 nhưng không gửi mail
        if (user.getAuthProvider() != AuthProvider.LOCAL
                || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return;
        }

        // 5. Tạo UUID token thô, lưu SHA-256 hash vào DB
        String rawToken    = UUID.randomUUID().toString();
        String hashedToken = HashUtil.sha256(rawToken);

        user.setResetToken(hashedToken);
        user.setResetTokenExp(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // 6. Gửi email bất đồng bộ (@Async trong EmailServiceImpl)
        //    Mail chứa UUID thô — DB chỉ lưu hash
        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

        log.info("Password reset email dispatched for userId={}", user.getId());
    }

    // ─── UC-03 Step 2: Reset Password ────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {

        // 1. Rate limit check (nếu cần, controller đã check — nhưng guard thêm ở đây)

        // 2. Hash token nhận được rồi tìm trong DB
        String hashedToken = HashUtil.sha256(rawToken);
        var userOpt = userRepository.findByResetTokenAndNotDeleted(hashedToken);

        // 3. Không tìm thấy hoặc user đã xóa mềm → 400 (cùng message, không lộ lý do)
        User user = userOpt.orElseThrow(() ->
                new BusinessException("Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        // 4. Kiểm tra token còn hạn
        if (user.getResetTokenExp() == null
                || LocalDateTime.now().isAfter(user.getResetTokenExp())) {
            throw new BusinessException("Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        // 5. Validate password policy
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("Mật khẩu mới không được để trống");
        }
        if (!InputNormalizer.isValidPassword(newPassword)) {
            throw new BusinessException(
                "Mật khẩu phải từ 8 đến 64 ký tự, gồm chữ hoa, số và ký tự đặc biệt");
        }

        // 6. Hash mật khẩu mới và cập nhật DB
        user.setPassword(passwordEncoder.encode(newPassword));

        // 7. Vô hiệu hoá token (single-use) + cập nhật password_changed_at
        //    password_changed_at = now() → mọi JWT cũ có iat < giá trị này đều bị từ chối
        user.setResetToken(null);
        user.setResetTokenExp(null);
        user.setPasswordChangedAt(LocalDateTime.now());

        userRepository.save(user);

        log.info("Password reset successfully for userId={}", user.getId());
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /**
     * Chuyển User entity → UserResponse.
     * avatarUrl được suy ra từ avatarKey (không lưu trong DB).
     */
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
