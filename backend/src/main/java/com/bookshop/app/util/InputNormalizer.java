package com.bookshop.app.util;

/**
 * Input normalization utilities per spec.
 * - email: trim + toLowerCase
 * - phoneNumber: convert +84 → 0, then validate
 * - fullName: trim
 */
public class InputNormalizer {

    private InputNormalizer() {}

    public static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    /**
     * Normalize phone: convert +84xxxxxxxxx → 0xxxxxxxxx.
     * Regex: ^0(3|5|7|8|9)\d{8}$
     */
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim();
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        }
        return phone;
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        return phone.matches("^0(3|5|7|8|9)\\d{8}$");
    }

    /**
     * Password policy: 8–64 chars, ≥1 uppercase, ≥1 digit, ≥1 special char.
     */
    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        int len = password.length();
        if (len < 8 || len > 64) return false;
        boolean hasUpper   = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.matches(".*[@#$!%^&*].*");
        return hasUpper && hasDigit && hasSpecial;
    }

    public static String normalizeName(String name) {
        if (name == null) return null;
        return name.trim();
    }
}
