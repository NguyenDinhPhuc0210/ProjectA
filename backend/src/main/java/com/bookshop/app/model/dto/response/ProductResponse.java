package com.bookshop.app.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO cho sản phẩm — Admin (đầy đủ) và Public (subset).
 * Dùng @JsonInclude(NON_NULL) để các field null không xuất hiện trong JSON.
 *
 * Admin response: tất cả field.
 * Public response (UC17 list): chỉ id, name, basePrice, category, productType, thumbnailUrl, inStock.
 * Public response (UC18 detail): thêm description, attributes, availableQuantity, createdAt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {

    // ─── Admin + Public ───────────────────────────────────────────────────────
    private Long           id;
    private String         name;
    private BigDecimal     basePrice;
    private CategoryRef    category;
    private String         productType;
    private String         thumbnailUrl;

    // ─── Public detail + Admin ────────────────────────────────────────────────
    private String         description;
    private Map<String, Object> attributes;
    private Boolean        inStock;
    private Integer        availableQuantity;  // stock - reserved (public detail)
    private String         createdAt;

    // ─── Admin only ───────────────────────────────────────────────────────────
    private String         sku;
    private String         status;
    private Integer        stockQuantity;
    private Long           version;
    private String         updatedAt;

    // ─── Nested DTO cho category ──────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRef {
        private Long   id;
        private String name;
    }
}
