package com.bookshop.app.repository;

import com.bookshop.app.model.entity.Product;
import com.bookshop.app.model.enums.ProductStatus;
import com.bookshop.app.model.enums.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * ProductRepository — @SQLRestriction trên entity đảm bảo mọi query mặc định lọc deleted_at IS NULL.
 *
 * Ngoại lệ: kiểm tra trùng SKU dùng native query KHÔNG lọc deleted_at —
 * sản phẩm đã xóa vẫn giữ SKU để đơn hàng cũ tham chiếu đúng.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * UC12 E3: Kiểm tra SKU đã tồn tại chưa — GỒM CẢ sản phẩm đã xóa mềm.
     * Dùng native query để bỏ qua @SQLRestriction.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM products WHERE sku = :sku", nativeQuery = true)
    boolean existsBySkuIncludingDeleted(@Param("sku") String sku);

    /**
     * UC13 Admin — Danh sách có filter động.
     * Tất cả param đều optional (null = bỏ qua điều kiện đó).
     * @SQLRestriction đảm bảo deleted_at IS NULL.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (:keyword    IS NULL OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:type       IS NULL OR p.productType = :type)
              AND (:status     IS NULL OR p.status = :status)
              AND (:minPrice   IS NULL OR p.basePrice >= :minPrice)
              AND (:maxPrice   IS NULL OR p.basePrice <= :maxPrice)
            """)
    Page<Product> findAdminList(
            @Param("keyword")    String keyword,
            @Param("categoryId") Long categoryId,
            @Param("type")       ProductType type,
            @Param("status")     ProductStatus status,
            @Param("minPrice")   BigDecimal minPrice,
            @Param("maxPrice")   BigDecimal maxPrice,
            Pageable pageable
    );

    /**
     * UC17 Public — Chỉ sản phẩm ACTIVE.
     * Keyword chỉ khớp name (không lộ SKU cho public).
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status = com.bookshop.app.model.enums.ProductStatus.ACTIVE
              AND (:keyword    IS NULL OR p.name LIKE %:keyword%)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:type       IS NULL OR p.productType = :type)
              AND (:minPrice   IS NULL OR p.basePrice >= :minPrice)
              AND (:maxPrice   IS NULL OR p.basePrice <= :maxPrice)
            """)
    Page<Product> findPublicList(
            @Param("keyword")    String keyword,
            @Param("categoryId") Long categoryId,
            @Param("type")       ProductType type,
            @Param("minPrice")   BigDecimal minPrice,
            @Param("maxPrice")   BigDecimal maxPrice,
            Pageable pageable
    );

    /**
     * UC18 Public detail — Chỉ trả sản phẩm ACTIVE (HIDDEN → 404 chung message).
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.id = :id
              AND p.status = com.bookshop.app.model.enums.ProductStatus.ACTIVE
            """)
    Optional<Product> findActiveById(@Param("id") Long id);
}
