package com.bookshop.app.repository;

import com.bookshop.app.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CategoryRepository — mọi truy vấn đều lọc deleted_at IS NULL qua @SQLRestriction trên entity.
 * Ngoại lệ: kiểm tra trùng name khi tạo/cập nhật cần phân biệt danh mục đang sửa.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Lấy toàn bộ danh mục còn hoạt động, sắp xếp theo name (UC10 public + UC11 admin list).
     * @SQLRestriction trên entity đảm bảo deleted_at IS NULL.
     */
    List<Category> findAllByOrderByNameAsc();

    /**
     * Đếm số sản phẩm ACTIVE của một danh mục — dùng cho UC10 (public productCount).
     * Native query để tránh phụ thuộc entity Product (chưa có khi compile nếu tách module).
     */
    @Query(value = """
            SELECT COUNT(*) FROM products
            WHERE category_id = :categoryId
              AND deleted_at IS NULL
              AND status = 'ACTIVE'
            """, nativeQuery = true)
    long countActiveProductsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Đếm TẤT CẢ sản phẩm chưa xóa (gồm cả HIDDEN) của một danh mục
     * — dùng khi xóa danh mục (UC11 E7: không xóa được nếu còn sản phẩm).
     */
    @Query(value = """
            SELECT COUNT(*) FROM products
            WHERE category_id = :categoryId
              AND deleted_at IS NULL
            """, nativeQuery = true)
    long countAllProductsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Kiểm tra tên danh mục đã tồn tại chưa (case-insensitive theo collation).
     * Loại trừ chính danh mục đang sửa (excludeId) để PUT không bị báo trùng với chính nó.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM Category c
            WHERE c.name = :name
              AND c.id != :excludeId
            """)
    boolean existsByNameExcludingId(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * Kiểm tra tên danh mục đã tồn tại chưa — dùng khi tạo mới (không cần excludeId).
     */
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.name = :name")
    boolean existsByName(@Param("name") String name);
}
