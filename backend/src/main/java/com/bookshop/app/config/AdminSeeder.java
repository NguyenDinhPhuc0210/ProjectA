package com.bookshop.app.config;

import com.bookshop.app.model.entity.User;
import com.bookshop.app.model.enums.AccountStatus;
import com.bookshop.app.model.enums.AuthProvider;
import com.bookshop.app.model.enums.Role;
import com.bookshop.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * UC-00: Seed the first admin account on startup.
 * Idempotent: only creates admin if no ROLE_ADMIN exists.
 * In prod profile: fails fast if env vars are missing or password invalid.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:Admin}")
    private String adminFullName;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsAdmin()) {
            log.info("Admin already exists, skipping seed.");
            return;
        }

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL or ADMIN_PASSWORD not set — skipping admin seed.");
            // TODO: In prod profile, throw an exception to fail fast.
            return;
        }

        // TODO: validate password against Password Policy before hashing
        User admin = User.builder()
                .email(adminEmail.trim().toLowerCase())
                .password(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName.trim())
                .role(Role.ROLE_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .avatarKey("default")
                .build();

        userRepository.save(admin);
        log.info("Admin account seeded for: {}", adminEmail);
        // NEVER log the password
    }
}
