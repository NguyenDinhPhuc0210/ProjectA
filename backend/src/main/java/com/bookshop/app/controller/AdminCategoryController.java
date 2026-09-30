package com.bookshop.app.controller;

import com.bookshop.app.model.dto.request.CategoryRequest;
import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.CategoryResponse;
import com.bookshop.app.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AdminCategoryController — Base URL: /api/v1/admin/categories
 *
 * Tất cả endpoint yêu cầu ROLE_ADMIN (bảo vệ bằng SecurityConfig + @PreAuthorize).
 *
 * UC11:
 *  POST   /api/v1/admin/categories          — Tạo danh mục
 *  GET    /api/v1/admin/categories          — Danh sách (gồm cả sản phẩm HIDDEN)
 *  GET    /api/v1/admin/categories/{id}     — Chi tiết
 *  PUT    /api/v1/admin/categories/{id}     — Cập nhật
 *  DELETE /api/v1/admin/categories/{id}     — Xóa mềm
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Categories", description = "Quản lý danh mục (chỉ Admin)")
@SecurityRequirement(name = "bearerAuth")
public class AdminCategoryController {

    private final CategoryService categoryService;

    // ─── Tạo danh mục ─────────────────────────────────────────────────────────

    @Operation(summary = "UC11: Tạo danh mục mới")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryRequest request) {

        CategoryResponse data = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo danh mục thành công", data));
    }

    // ─── Danh sách ────────────────────────────────────────────────────────────

    @Operation(summary = "UC11: Danh sách danh mục (Admin — gồm cả sản phẩm ẩn)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> list() {
        List<CategoryResponse> data = categoryService.listAdmin();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục thành công", data));
    }

    // ─── Chi tiết ─────────────────────────────────────────────────────────────

    @Operation(summary = "UC11: Chi tiết danh mục")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(
            @PathVariable Long id) {

        CategoryResponse data = categoryService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết danh mục thành công", data));
    }

    // ─── Cập nhật ─────────────────────────────────────────────────────────────

    @Operation(summary = "UC11: Cập nhật danh mục")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {

        CategoryResponse data = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục thành công", data));
    }

    // ─── Xóa mềm ──────────────────────────────────────────────────────────────

    @Operation(summary = "UC11: Xóa danh mục (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id) {

        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh mục thành công", null));
    }
}
