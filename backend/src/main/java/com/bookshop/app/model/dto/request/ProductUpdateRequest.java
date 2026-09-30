package com.bookshop.app.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body cho UC14 — Cập nhật sản phẩm (Admin).
 *
 * Field được sửa   : name, description, basePrice, categoryId, thumbnailUrl, attributes.
 * Field bị bỏ qua  : sku, productType, stockQuantity, status (gửi lên cũng không có tác dụng).
 *
 * version bắt buộc — dùng cho optimistic locking.
 * thumbnailUrl = null  → xóa ảnh.
 * description  = null  → xóa mô tả.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(min = 2, max = 255, message = "Tên sản phẩm phải từ 2 đến 255 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "1", message = "Giá không hợp lệ")
    @DecimalMax(value = "1000000000", message = "Giá không hợp lệ")
    private BigDecimal basePrice;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    /** null = xóa ảnh. Validate http/https ở Service. */
    private String thumbnailUrl;

    @NotNull(message = "Thuộc tính sản phẩm không được để trống")
    private Map<String, Object> attributes;

    /** Bắt buộc — optimistic locking. Thiếu hoặc null → 400. */
    @NotNull(message = "Thiếu thông tin phiên bản dữ liệu")
    private Long version;
}
