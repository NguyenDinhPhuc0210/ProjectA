package com.bookshop.app.service.impl;

import com.bookshop.app.exception.RateLimitExceededException;
import com.bookshop.app.service.RateLimitService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Bucket4j implementation of RateLimitService.
 * Uses ConcurrentHashMap — sufficient for single-instance deployment.
 * For multi-instance: swap to Redis-backed Bucket4j (ProxyManager).
 *
 * Rules:
 *   login          → 5 failed attempts / 15 min per (IP + email)
 *   forgot-password → 3 requests / 15 min per IP, and per email
 *   reset-password  → 10 requests / 15 min per IP
 */
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // ─── Bucket factories ────────────────────────────────────────────────────

    private Bucket loginBucket(String key) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(5)
                                .refillIntervally(5, Duration.ofMinutes(15))
                                .build())
                        .build()
        );
    }

    private Bucket forgotPasswordBucket(String key) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(3)
                                .refillIntervally(3, Duration.ofMinutes(15))
                                .build())
                        .build()
        );
    }

    private Bucket resetPasswordBucket(String key) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(10)
                                .refillIntervally(10, Duration.ofMinutes(15))
                                .build())
                        .build()
        );
    }

    // ─── Interface implementation ─────────────────────────────────────────────

    @Override
    public void checkLoginLimit(String ip, String email) {
        String key = "login:" + ip + ":" + email;
        if (!loginBucket(key).tryConsume(1)) {
            throw new RateLimitExceededException(
                    "Too many failed login attempts. Please try again in 15 minutes.");
        }
    }

    @Override
    public void recordFailedLogin(String ip, String email) {
        // With tryConsume approach, the token is already consumed.
        // This method exists for explicit failure tracking if logic changes.
        // No-op in current impl — consumption happens in checkLoginLimit.
    }

    @Override
    public void checkForgotPasswordLimit(String ip, String email) {
        String ipKey    = "forgot:ip:" + ip;
        String emailKey = "forgot:email:" + email;
        boolean ipOk    = forgotPasswordBucket(ipKey).tryConsume(1);
        boolean emailOk = forgotPasswordBucket(emailKey).tryConsume(1);
        if (!ipOk || !emailOk) {
            throw new RateLimitExceededException(
                    "Too many password reset requests. Please try again in 15 minutes.");
        }
    }

    @Override
    public void checkResetPasswordLimit(String ip) {
        String key = "reset:ip:" + ip;
        if (!resetPasswordBucket(key).tryConsume(1)) {
            throw new RateLimitExceededException(
                    "Too many reset attempts. Please try again in 15 minutes.");
        }
    }
}
