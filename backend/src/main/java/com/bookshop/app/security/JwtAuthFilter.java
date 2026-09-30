package com.bookshop.app.security;

import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.enums.AccountStatus;
import com.bookshop.app.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * JwtAuthFilter — chạy 1 lần mỗi request (OncePerRequestFilter).
 *
 * Theo spec (UC xác thực JWT):
 *   1. User không tồn tại hoặc deleted_at IS NOT NULL → 401
 *   2. account_status = LOCKED → 403
 *   3. token.iat < password_changed_at → 401 (token phát hành trước khi đổi/reset mật khẩu)
 *
 * Principal trong SecurityContext là userId (Long).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil        jwtUtil;
    private final UserRepository userRepository;
    private final ObjectMapper   objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // Không có token → tiếp tục filter chain (public endpoint hoặc sẽ bị Security chặn)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Parse JWT ──────────────────────────────────────────────────────────
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (ExpiredJwtException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Token không hợp lệ");
            return;
        }

        // ── Lấy userId từ claims ───────────────────────────────────────────────
        Long userId;
        try {
            userId = ((Number) claims.get("userId")).longValue();
        } catch (Exception ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token không hợp lệ");
            return;
        }

        // ── Re-query user trong DB (theo spec: mỗi request đều phải query lại) ─
        var userOpt = userRepository.findByIdAndNotDeleted(userId);

        if (userOpt.isEmpty()) {
            // User không tồn tại hoặc đã soft-delete → 401
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token không hợp lệ");
            return;
        }

        var user = userOpt.get();

        // ── Kiểm tra tài khoản bị khóa → 403 ─────────────────────────────────
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ");
            return;
        }

        // ── Kiểm tra token phát hành trước khi đổi mật khẩu → 401 ────────────
        // iat trong JWT là giây (epoch), password_changed_at là LocalDateTime
        if (user.getPasswordChangedAt() != null) {
            Date issuedAt = claims.getIssuedAt();
            if (issuedAt != null) {
                LocalDateTime tokenIat = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(issuedAt.getTime()), ZoneId.systemDefault());
                if (tokenIat.isBefore(user.getPasswordChangedAt())) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại");
                    return;
                }
            }
        }

        // ── Set Authentication vào SecurityContext ────────────────────────────
        // Principal = userId (Long) — UserController dùng @AuthenticationPrincipal Long userId
        var auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    /** Trích xuất Bearer token từ Authorization header. */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /** Ghi JSON error response trực tiếp (bypass Spring MVC). */
    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message));
    }
}
