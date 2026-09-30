package com.bookshop.app.controller;

import com.bookshop.app.model.dto.request.ProductCreateRequest;
import com.bookshop.app.model.dto.request.ProductStatusRequest;
import com.bookshop.app.model.dto.request.ProductUpdateRequest;
import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.PageResponse;
import com.bookshop.app.model.dto.response.ProductResponse;
import com.bookshop.app.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * AdminProductController — Base URL: /api/v1/admin/products
 *
 * Tất cả endpoint yêu cầu ROLE_ADMIN.
 *
 * UC12: POST /api/v1/admin/products — Tạo sản phẩm
 * (UC13–UC16 sẽ thêm vào tiếp theo)
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Products", description = "Quản lý sản phẩm (chỉ Admin)")
@SecurityRequirement(name = "bearerAuth")
public class AdminProductController {

    private final ProductService productService;

    // ─── UC12 — Tạo sản phẩm ─────────────────────────────────────────────────

    @Operation(summary = "UC12: Tạo sản phẩm mới (BOOK hoặc HOBBY)")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductCreateRequest request) {

        ProductResponse data = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công", data));
    }

    // ─── UC13 — Danh sách sản phẩm (Admin) ─────────────────────────────────

    @Operation(summary = "UC13: Danh sách sản phẩm có filter (Admin, thấy cả HIDDEN)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listAdmin(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        PageResponse<ProductResponse> data = productService.listAdmin(
                keyword, categoryId, productType, status,
                minPrice, maxPrice, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", data));
    }

    // ─── UC13 — Chi tiết sản phẩm (Admin) ──────────────────────────────────

    @Operation(summary = "UC13: Chi tiết sản phẩm (Admin, thấy cả HIDDEN)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(
            @PathVariable Long id) {

        ProductResponse data = productService.getByIdAdmin(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", data));
    }

    // ─── UC14 — Cập nhật sản phẩm ────────────────────────────────────

    @Operation(summary = "UC14: Cập nhật thông tin sản phẩm (optimistic locking)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {

        ProductResponse data = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", data));
    }

    // ─── UC15 — Ẩn / hiện sản phẩm ─────────────────────────────────────

    @Operation(summary = "UC15: Ẩn / hiện sản phẩm (ACTIVE / HIDDEN)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusRequest request) {

        ProductResponse data = productService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái sản phẩm thành công", data));
    }

    // ─── UC16 — Xóa mềm sản phẩm ─────────────────────────────────────

    @Operation(summary = "UC16: Xóa mềm sản phẩm")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id) {

        productService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm thành công", null));
    }
}
