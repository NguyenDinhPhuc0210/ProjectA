package com.bookshop.app.service.validator;

import com.bookshop.app.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validator cho HOBBY attributes.
 *
 * Schema bắt buộc:
 *   brand    : string, 1–100 ký tự
 *   scale    : "1/7", "1/72"... (regex \d{1,3}/\d{1,4}), chấp nhận "1:7" → đổi "1/7", hoặc "N/A"
 *   material : string, 1–100 ký tự
 *
 * Key lạ → 400.
 */
@Component
public class HobbyAttributesValidator implements AttributesValidator {

    private static final Set<String> ALLOWED_KEYS = Set.of("brand", "scale", "material");

    /** Dạng 1/7, 1/72, 1/144 — mẫu đã đổi ':' thành '/' */
    private static final Pattern SCALE_PATTERN = Pattern.compile("^\\d{1,3}/\\d{1,4}$");

    @Override
    public void validate(Map<String, Object> attributes) {
        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Kiểm tra key lạ
        for (String key : attributes.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                errors.put("attributes." + key, "Thuộc tính không hợp lệ cho hobby: " + key);
            }
        }

        // 2. Kiểm tra key bắt buộc
        validateStringField(attributes, "brand",    1, 100, errors);
        validateStringField(attributes, "material", 1, 100, errors);
        validateScaleField (attributes, errors);

        if (!errors.isEmpty()) {
            throw new BusinessException("Thuộc tính sản phẩm không hợp lệ", errors);
        }
    }

    @Override
    public Map<String, Object> normalize(Map<String, Object> attributes) {
        Map<String, Object> result = new HashMap<>(attributes);

        trimString(result, "brand");
        trimString(result, "material");

        // Chuẩn hoá scale: "1:7" → "1/7", trim
        if (result.get("scale") instanceof String scale) {
            scale = scale.trim().replace(':', '/');
            result.put("scale", scale);
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

    private void validateScaleField(Map<String, Object> attrs, Map<String, String> errors) {
        Object val = attrs.get("scale");
        if (val == null || !(val instanceof String scale) || scale.isBlank()) {
            errors.put("attributes.scale", "'scale' không được để trống");
            return;
        }
        // Cho phép "N/A" (boardgame không có tỷ lệ)
        String normalized = scale.trim().replace(':', '/');
        if (!normalized.equalsIgnoreCase("N/A") && !SCALE_PATTERN.matcher(normalized).matches()) {
            errors.put("attributes.scale",
                    "'scale' phải theo dạng '1/7', '1/72'... hoặc 'N/A'");
        }
    }

    private void trimString(Map<String, Object> attrs, String key) {
        if (attrs.get(key) instanceof String val) {
            attrs.put(key, val.trim());
        }
    }
}
