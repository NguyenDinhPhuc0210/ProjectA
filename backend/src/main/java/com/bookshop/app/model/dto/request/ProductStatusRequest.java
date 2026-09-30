package com.bookshop.app.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body cho UC15 — Ẩn / hiện sản phẩm.
 * Chỉ chứa trường status; giá trị hợp lệ: "ACTIVE" hoặc "HIDDEN".
 * Validate thực tế (enum check) thực hiện ở Service để trả message đúng spec.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatusRequest {

    @NotBlank(message = "Trạng thái không hợp lệ")
    private String status;
}
