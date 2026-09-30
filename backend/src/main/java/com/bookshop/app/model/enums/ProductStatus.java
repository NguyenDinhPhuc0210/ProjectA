package com.bookshop.app.model.enums;

/**
 * Trạng thái hiển thị sản phẩm.
 * ACTIVE : đang bán, khách thấy.
 * HIDDEN : ẩn tạm thời — khách không thấy (API public trả 404), Admin vẫn thấy và sửa được.
 */
public enum ProductStatus {
    ACTIVE,
    HIDDEN
}
