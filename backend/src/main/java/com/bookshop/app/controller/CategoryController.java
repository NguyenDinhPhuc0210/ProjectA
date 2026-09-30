package com.bookshop.app.controller;

import com.bookshop.app.model.dto.request.CategoryRequest;
import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.CategoryResponse;
import com.bookshop.app.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PublicCategoryController — Base URL: /api/v1/categories
 *
 * UC10: GET /api/v1/categories — Xem danh sách danh mục (Public, không cần auth)
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories (Public)", description = "Danh sách danh mục — dành cho mọi người")
public class CategoryController {

    private final CategoryService categoryService;

    // ─── UC10 — Xem danh sách danh mục (Public) ──────────────────────────────

    @Operation(summary = "UC10: Xem danh sách danh mục (Public)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listCategories() {
        List<CategoryResponse> data = categoryService.listPublic();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục thành công", data));
    }
}
