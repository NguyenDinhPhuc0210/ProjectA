package com.bookshop.app.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO dùng cho UC10 (Public) và UC11 (Admin).
 * productCount: số sản phẩm ACTIVE (UC10) hoặc gồm cả HIDDEN (UC11).
 * createdAt / updatedAt: chỉ trả trong Admin detail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryResponse {

    private Long   id;
    private String name;
    private String description;
    private Long   productCount;

    // Timestamps — chỉ set khi Admin cần
    private String createdAt;
    private String updatedAt;
}
