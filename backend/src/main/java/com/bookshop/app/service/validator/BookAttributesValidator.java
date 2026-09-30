package com.bookshop.app.service.validator;

import com.bookshop.app.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validator cho BOOK attributes.
 *
 * Schema bắt buộc:
 *   author    : string, 1–255 ký tự
 *   publisher : string, 1–255 ký tự
 *   pages     : integer, 1–10000
 *   isbn      : ISBN-10 hoặc ISBN-13 hợp lệ (đúng checksum)
 *
 * Key lạ → 400.
 */
@Component
public class BookAttributesValidator implements AttributesValidator {

    private static final Set<String> ALLOWED_KEYS = Set.of("author", "publisher", "pages", "isbn");

    @Override
    public void validate(Map<String, Object> attributes) {
        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Kiểm tra key lạ
        for (String key : attributes.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                errors.put("attributes." + key, "Thuộc tính không hợp lệ cho sách: " + key);
            }
        }

        // 2. Kiểm tra key bắt buộc
        validateStringField(attributes, "author",    1, 255, errors);
        validateStringField(attributes, "publisher", 1, 255, errors);
        validatePagesField (attributes, errors);
        validateIsbnField  (attributes, errors);

        if (!errors.isEmpty()) {
            throw new BusinessException("Thuộc tính sản phẩm không hợp lệ", errors);
        }
    }

    @Override
    public Map<String, Object> normalize(Map<String, Object> attributes) {
        Map<String, Object> result = new HashMap<>(attributes);

        // Trim các string
        trimString(result, "author");
        trimString(result, "publisher");

        // Chuẩn hoá ISBN: xóa dấu '-' và khoảng trắng
        if (result.get("isbn") instanceof String isbn) {
            result.put("isbn", isbn.replaceAll("[\\s-]", "").toUpperCase());
        }

        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void validateStringField(Map<String, Object> attrs, String key,
                                     int min, int max, Map<String, String> errors) {
        Object val = attrs.get(key);
        if (val == null || !(val instanceof String str) || str.isBlank()) {
            errors.put("attributes." + key, "'" + key + "' không được để trống");
            return;
        }
        str = str.trim();
        if (str.length() < min || str.length() > max) {
            errors.put("attributes." + key,
                    "'" + key + "' phải từ " + min + " đến " + max + " ký tự");
        }
    }

    private void validatePagesField(Map<String, Object> attrs, Map<String, String> errors) {
        Object val = attrs.get("pages");
        if (val == null) {
            errors.put("attributes.pages", "'pages' không được để trống");
            return;
        }
        int pages;
        try {
            // Jackson có thể parse thành Integer hoặc Long
            pages = ((Number) val).intValue();
        } catch (ClassCastException e) {
            errors.put("attributes.pages", "'pages' phải là số nguyên");
            return;
        }
        if (pages < 1 || pages > 10_000) {
            errors.put("attributes.pages", "'pages' phải từ 1 đến 10000");
        }
    }

    private void validateIsbnField(Map<String, Object> attrs, Map<String, String> errors) {
        Object val = attrs.get("isbn");
        if (val == null || !(val instanceof String isbn) || isbn.isBlank()) {
            errors.put("attributes.isbn", "'isbn' không được để trống");
            return;
        }
        // Chuẩn hoá trước khi kiểm tra
        String normalized = isbn.replaceAll("[\\s-]", "").toUpperCase();
        if (!isValidIsbn10(normalized) && !isValidIsbn13(normalized)) {
            errors.put("attributes.isbn", "ISBN không hợp lệ (phải là ISBN-10 hoặc ISBN-13 đúng checksum)");
        }
    }

    /**
     * ISBN-10: 9 chữ số + check digit (0-9 hoặc X).
     * Checksum: Σ(i * d_i) mod 11 == 0, i từ 10 đến 1.
     */
    static boolean isValidIsbn10(String isbn) {
        if (isbn.length() != 10) return false;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = isbn.charAt(i);
            if (!Character.isDigit(c)) return false;
            sum += (10 - i) * (c - '0');
        }
        char last = isbn.charAt(9);
        if (last == 'X') {
            sum += 10;
        } else if (Character.isDigit(last)) {
            sum += (last - '0');
        } else {
            return false;
        }
        return sum % 11 == 0;
    }

    /**
     * ISBN-13: 13 chữ số.
     * Checksum: Σ(w_i * d_i) mod 10 == 0, w_i = 1 hoặc 3 xen kẽ.
     */
    static boolean isValidIsbn13(String isbn) {
        if (isbn.length() != 13) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = isbn.charAt(i);
            if (!Character.isDigit(c)) return false;
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += weight * (c - '0');
        }
        return sum % 10 == 0;
    }

    private void trimString(Map<String, Object> attrs, String key) {
        if (attrs.get(key) instanceof String val) {
            attrs.put(key, val.trim());
        }
    }
}
