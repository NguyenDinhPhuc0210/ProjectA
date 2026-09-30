package com.bookshop.app.service.impl;

import com.bookshop.app.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * EmailServiceImpl — uses JavaMailSender with @Async.
 * Response time does NOT reveal whether the email exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    @Async
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        try {
            String resetLink = "http://localhost:5173/reset-password?token=" + rawToken;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset Request — Book & Hobby Shop");
            message.setText(
                    "Hello,\n\n" +
                    "You requested a password reset. Click the link below to reset your password:\n\n" +
                    resetLink + "\n\n" +
                    "This link expires in 15 minutes.\n\n" +
                    "If you did not request this, please ignore this email.\n\n" +
                    "Book & Hobby Shop Team"
            );
            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            // Do NOT re-throw — caller gets 200 regardless
        }
    }
}
