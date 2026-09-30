package com.bookshop.app.service;

import com.bookshop.app.model.dto.request.ProductCreateRequest;
import com.bookshop.app.model.dto.request.ProductUpdateRequest;
import com.bookshop.app.model.dto.response.PageResponse;
import com.bookshop.app.model.dto.response.ProductResponse;

import java.math.BigDecimal;

/**
 * ProductService — UC12 + UC13.
 */
public interface ProductService {

    // ─── UC12: Tạo sản phẩm ──────────────────────────────────────────────────
    /**
     * Tạo sản phẩm mới.
     * - Validate SKU format (đã check ở Controller qua Bean Validation).
     * - Kiểm tra SKU trùng (gồm cả sản phẩm đã xóa).
     * - Validate attributes theo productType (Strategy pattern).
     * - Lưu với stock_quantity = 0, status = ACTIVE.
     */
    ProductResponse create(ProductCreateRequest request);

    // ─── UC13: Xem danh sách & chi tiết sản phẩm (Admin) ────────────────────

    /**
     * Danh sách sản phẩm Admin — filter đa chiều, phân trang.
     * Gồm cả sản phẩm HIDDEN. Keyword khớp name HOẶC sku.
     *
     * @param keyword    tìm trong name/sku (null = bỏ qua)
     * @param categoryId lọc theo danh mục (null = tất cả)
     * @param productType "BOOK" | "HOBBY" | null
     * @param status      "ACTIVE" | "HIDDEN" | null (cả hai)
     * @param minPrice    giá tối thiểu (null = không giới hạn)
     * @param maxPrice    giá tối đa (null = không giới hạn)
     * @param sort        "newest" | "price_asc" | "price_desc"
     * @param page        trang (bắt đầu từ 0)
     * @param size        kích thước trang (1–50)
     */
    PageResponse<ProductResponse> listAdmin(
            String keyword, Long categoryId, String productType, String status,
            BigDecimal minPrice, BigDecimal maxPrice,
            String sort, int page, int size);

    /**
     * Chi tiết sản phẩm theo id (Admin) — thấy cả HIDDEN.
     * Ném 404 nếu không tồn tại hoặc đã xóa mềm.
     */
    ProductResponse getByIdAdmin(Long id);

    // ─── UC14: Cập nhật sản phẩm (Admin) ─────────────────────────────────────

    /**
     * Cập nhật sản phẩm (Admin).
     * - Chỉ sửa: name, description, basePrice, categoryId, thumbnailUrl, attributes.
     * - Validate attributes theo productType GỐC của sản phẩm (không đổi type).
     * - Kiểm tra version khớp → 409 nếu không khớp (optimistic locking).
     */
    ProductResponse update(Long id, ProductUpdateRequest request);

    // ─── UC15: Ẩn / hiện sản phẩm (Admin) ────────────────────────────────────

    /**
     * Cập nhật status = ACTIVE hoặc HIDDEN.
     * Idempotent: đặt trùng status hiện tại vẫn trả 200.
     * Trả về DTO nhẹ chỉ gồm id + status.
     */
    ProductResponse updateStatus(Long id, String status);

    // ─── UC16: Xóa mềm sản phẩm (Admin) ─────────────────────────────────────

    /**
     * Xóa mềm: set deleted_at = now().
     * SKU vẫn giữ nguyên (đơn hàng cũ vẫn tham chiếu đúng mã).
     * Ném 404 nếu sản phẩm đã xóa hoặc không tồn tại.
     */
    void softDelete(Long id);

    // ─── UC17: Tìm kiếm & lọc sản phẩm (Public) ──────────────────────────────

    /**
     * Danh sách sản phẩm Public — chỉ ACTIVE, không lộ sku/stockQuantity/version/status.
     * Keyword chỉ khớp name (không để lộ SKU).
     * inStock = (stockQuantity > 0).
     */
    PageResponse<ProductResponse> listPublic(
            String keyword, Long categoryId, String productType,
            BigDecimal minPrice, BigDecimal maxPrice,
            String sort, int page, int size);

    // ─── UC18: Xem chi tiết sản phẩm (Public) ────────────────────────────────

    /**
     * Chi tiết sản phẩm Public — chỉ ACTIVE.
     * E1/E2/E3 (không tồn tại / đã xóa / HIDDEN) dùng chung 1 message "Sản phẩm không tồn tại".
     * Trả thêm attributes, availableQuantity (stock - reserved), createdAt.
     */
    ProductResponse getPublicById(Long id);
}
