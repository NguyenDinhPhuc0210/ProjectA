package com.bookshop.app.model.dto.response;

import lombok.*;

import java.util.List;

/**
 * Generic paginated response — dùng cho mọi API danh sách có phân trang.
 *
 * Wrapped trong ApiResponse:
 * {
 *   "success": true,
 *   "message": "...",
 *   "data": {
 *     "items": [...],
 *     "page": 0,
 *     "size": 12,
 *     "totalElements": 45,
 *     "totalPages": 4
 *   }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private int     page;
    private int     size;
    private long    totalElements;
    private int     totalPages;

    /** Factory helper — tạo từ Spring Page. */
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> springPage) {
        return PageResponse.<T>builder()
                .items(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .build();
    }
}
