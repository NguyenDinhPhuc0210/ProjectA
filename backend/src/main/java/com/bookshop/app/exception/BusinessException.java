package com.bookshop.app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/** 400 Bad Request — business rule violations (not validation). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
@Getter
public class BusinessException extends RuntimeException {

    /** Optional field-level errors (dùng cho attributes validation). */
    private final Map<String, String> errors;

    public BusinessException(String message) {
        super(message);
        this.errors = null;
    }

    public BusinessException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors;
    }
}
