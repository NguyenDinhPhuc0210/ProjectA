package com.bookshop.app.model.dto.request;

import com.bookshop.app.model.enums.ProductType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body cho UC12 — Tạo sản phẩm (Admin).
 *
 * Các field bị bỏ qua nếu client gửi: status, stockQuantity, reservedQuantity, version.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "SKU không được để trống")
    @Pattern(
        regexp = "^[A-Z0-9\\-]{3,30}$",
        message = "SKU chỉ gồm chữ hoa, số, dấu gạch ngang, dài 3–30 ký tự"
    )
    private String sku;

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

    @NotNull(message = "Loại sản phẩm không được để trống")
    private ProductType productType;

    /** URL ảnh — có thể null. Validate http/https ở Service để trả message đúng spec. */
    private String thumbnailUrl;

    @NotNull(message = "Thuộc tính sản phẩm không được để trống")
    private Map<String, Object> attributes;
}
