package com.bookshop.app.service;

/**
 * RateLimitService — wraps Bucket4j logic for login, forgot-password, reset-password.
 *
 * Rate limit rules (per spec):
 *   POST /auth/login          → 5 failed attempts / 15 min per (IP + email)
 *   POST /auth/forgot-password → 3 requests / 15 min per IP AND per email
 *   POST /auth/reset-password  → 10 requests / 15 min per IP
 *
 * Keys are composite strings, e.g. "login:192.168.1.1:user@email.com"
 */
public interface RateLimitService {

    /**
     * Check if login is allowed for this IP + email combo.
     * Should be called BEFORE authentication (per spec: check rate limit first).
     * @throws com.bookshop.app.exception.RateLimitExceededException if limit reached
     */
    void checkLoginLimit(String ip, String email);

    /**
     * Record a FAILED login attempt (increments the bucket counter).
     * Only called when authentication actually fails.
     */
    void recordFailedLogin(String ip, String email);

    /**
     * Check forgot-password rate limit for both IP and email separately.
     * @throws com.bookshop.app.exception.RateLimitExceededException if limit reached
     */
    void checkForgotPasswordLimit(String ip, String email);

    /**
     * Check reset-password rate limit for IP only.
     * @throws com.bookshop.app.exception.RateLimitExceededException if limit reached
     */
    void checkResetPasswordLimit(String ip);
}
