package com.bookshop.app.service;

import com.bookshop.app.model.dto.request.CategoryRequest;
import com.bookshop.app.model.dto.response.CategoryResponse;

import java.util.List;

/**
 * CategoryService — UC10 (Public) + UC11 (Admin CRUD).
 */
public interface CategoryService {

    // ─── UC10: Xem danh sách danh mục (Public) ───────────────────────────────

    /**
     * Trả toàn bộ danh mục chưa xóa, sắp xếp theo name.
     * productCount = chỉ đếm sản phẩm ACTIVE (public không thấy HIDDEN).
     */
    List<CategoryResponse> listPublic();

    // ─── UC11: Quản lý danh mục (Admin) ──────────────────────────────────────

    /** Tạo danh mục mới. Trả 409 nếu name đã tồn tại. */
    CategoryResponse create(CategoryRequest request);

    /** Danh sách Admin: productCount gồm cả HIDDEN. */
    List<CategoryResponse> listAdmin();

    /** Chi tiết danh mục theo id (Admin). Trả 404 nếu không tồn tại / đã xóa. */
    CategoryResponse getById(Long id);

    /** Cập nhật name/description. Trả 409 nếu name mới trùng danh mục khác. */
    CategoryResponse update(Long id, CategoryRequest request);

    /**
     * Xóa mềm: name → name#deleted_<id>, deleted_at = now().
     * Từ chối (400) nếu danh mục còn sản phẩm chưa xóa (gồm cả HIDDEN).
     */
    void delete(Long id);
}
