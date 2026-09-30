package com.bookshop.app.controller;

import com.bookshop.app.model.dto.response.ApiResponse;
import com.bookshop.app.model.dto.response.PageResponse;
import com.bookshop.app.model.dto.response.ProductResponse;
import com.bookshop.app.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * ProductController (Public) — Base URL: /api/v1/products
 *
 * UC17: GET /api/v1/products          — Tìm kiếm & lọc sản phẩm (Public)
 * UC18: GET /api/v1/products/{id}     — Xem chi tiết sản phẩm (Public)
 *
 * Không yêu cầu auth; SecurityConfig đã mở permitAll cho /api/v1/products/**.
 * Chỉ trả sản phẩm ACTIVE — HIDDEN và đã xóa đều → 404.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products (Public)", description = "Sản phẩm công khai — dành cho mọi người")
public class ProductController {

    private final ProductService productService;

    // ─── UC17 — Tìm kiếm & lọc sản phẩm ─────────────────────────────────────

    @Operation(summary = "UC17: Tìm kiếm & lọc sản phẩm (chỉ ACTIVE, không cần auth)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listPublic(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        PageResponse<ProductResponse> data = productService.listPublic(
                keyword, categoryId, productType, minPrice, maxPrice, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", data));
    }

    // ─── UC18 — Xem chi tiết sản phẩm ────────────────────────────────────────

    @Operation(summary = "UC18: Xem chi tiết sản phẩm (chỉ ACTIVE, không cần auth)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getPublicById(
            @PathVariable Long id) {

        ProductResponse data = productService.getPublicById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", data));
    }
}
