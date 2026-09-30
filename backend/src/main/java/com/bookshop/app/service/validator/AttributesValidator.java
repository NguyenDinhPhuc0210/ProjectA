package com.bookshop.app.service.validator;

import java.util.Map;

/**
 * Strategy interface cho validation attributes theo product_type.
 * Mỗi implementor validate đúng schema của BOOK hoặc HOBBY.
 * Lỗi được thu thập vào Map<field, message> và ném BusinessException nếu có lỗi.
 */
public interface AttributesValidator {

    /**
     * Validate map attributes.
     * @param attributes Map raw từ request (key → value, value có thể là String, Number, ...)
     * @throws com.bookshop.app.exception.BusinessException nếu có lỗi validation
     */
    void validate(Map<String, Object> attributes);

    /**
     * Chuẩn hoá attributes sau khi validate thành công (trim string, format isbn, ...).
     * Trả về Map mới đã chuẩn hoá — không sửa trực tiếp tham số đầu vào.
     */
    Map<String, Object> normalize(Map<String, Object> attributes);
}
