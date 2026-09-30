package com.bookshop.app.service;

/**
 * EmailService — sends emails asynchronously via @Async.
 * Implementations must NOT block the caller.
 */
public interface EmailService {

    /**
     * Send password reset email.
     * @param toEmail  recipient
     * @param rawToken the raw UUID token (NOT the hash) — included in the link
     */
    void sendPasswordResetEmail(String toEmail, String rawToken);
}
