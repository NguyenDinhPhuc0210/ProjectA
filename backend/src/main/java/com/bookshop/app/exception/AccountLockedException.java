package com.bookshop.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 403 — Tài khoản bị LOCKED (sau khi xác thực đúng password).
 * Dùng 403 chứ không phải 401 để Frontend không auto-logout.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
