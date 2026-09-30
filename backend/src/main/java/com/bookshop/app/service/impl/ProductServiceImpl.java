package com.bookshop.app.service.impl;

import com.bookshop.app.exception.BusinessException;
import com.bookshop.app.exception.DuplicateResourceException;
import com.bookshop.app.exception.ResourceNotFoundException;
import com.bookshop.app.model.dto.request.ProductCreateRequest;
import com.bookshop.app.model.dto.request.ProductUpdateRequest;
import com.bookshop.app.model.dto.response.PageResponse;
import com.bookshop.app.model.dto.response.ProductResponse;
import com.bookshop.app.model.entity.Category;
import com.bookshop.app.model.entity.Product;
import com.bookshop.app.model.enums.ProductStatus;
import com.bookshop.app.model.enums.ProductType;
import com.bookshop.app.repository.CategoryRepository;
import com.bookshop.app.repository.ProductRepository;
import com.bookshop.app.service.ProductService;
import com.bookshop.app.service.validator.AttributesValidator;
import com.bookshop.app.service.validator.AttributesValidatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ProductServiceImpl — UC12: Tạo sản phẩm.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final Pattern URL_PATTERN =
            Pattern.compile("^https?://.{1,490}$");

    private final ProductRepository        productRepository;
    private final CategoryRepository       categoryRepository;
    private final AttributesValidatorFactory validatorFactory;

    // ─── UC12: Tạo sản phẩm ──────────────────────────────────────────────────

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest req) {

        // 1. Chuẩn hoá SKU
        String sku = req.getSku().trim().toUpperCase();

        // 2. Kiểm tra SKU trùng (gồm cả sản phẩm đã xóa)
        if (productRepository.existsBySkuIncludingDeleted(sku)) {
            throw new DuplicateResourceException("sku", "SKU đã tồn tại");
        }

        // 3. Validate & lấy Category
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new BusinessException("Danh mục không tồn tại"));

        // 4. Validate thumbnailUrl (nếu có)
        String thumbnailUrl = req.getThumbnailUrl();
        if (thumbnailUrl != null) {
            thumbnailUrl = thumbnailUrl.trim();
            if (!thumbnailUrl.isEmpty() && !URL_PATTERN.matcher(thumbnailUrl).matches()) {
                throw new BusinessException("URL ảnh sản phẩm không hợp lệ");
            }
            if (thumbnailUrl.isEmpty()) thumbnailUrl = null;
        }

        // 5. Validate + chuẩn hoá attributes theo productType (Strategy)
        AttributesValidator validator = validatorFactory.getValidator(req.getProductType());
        validator.validate(req.getAttributes());
        Map<String, Object> normalizedAttributes = validator.normalize(req.getAttributes());

        // 6. Chuẩn hoá name, description
        String name = req.getName().trim();
        String description = req.getDescription() != null ? req.getDescription().trim() : null;
        if (description != null && description.isEmpty()) description = null;

        // 7. Tạo entity và lưu
        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .description(description)
                .basePrice(req.getBasePrice())
                .category(category)
                .productType(req.getProductType())
                .status(ProductStatus.ACTIVE)
                .thumbnailUrl(thumbnailUrl)
                .attributes(normalizedAttributes)
                .stockQuantity(0)
                .reservedQuantity(0)
                .build();

        try {
            product = productRepository.save(product);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("uk_products_sku")) {
                throw new DuplicateResourceException("sku", "SKU đã tồn tại");
            }
            throw ex;
        }

        log.info("Product created: id={}, sku={}, type={}", product.getId(), sku, req.getProductType());
        return toAdminResponse(product);
    }

    // ─── UC13: Danh sách & chi tiết sản phẩm (Admin) ────────────────────────

    @Override
    public PageResponse<ProductResponse> listAdmin(
            String keyword, Long categoryId, String productType, String status,
            BigDecimal minPrice, BigDecimal maxPrice,
            String sort, int page, int size) {

        // ── Validate tham số phân trang ──────────────────────────────────────
        if (page < 0) throw new BusinessException("Tham số truy vấn không hợp lệ");
        if (size < 1 || size > 50) throw new BusinessException("Tham số truy vấn không hợp lệ");

        // ── Validate khoảng giá ──────────────────────────────────────────────
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Khoảng giá không hợp lệ");
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Khoảng giá không hợp lệ");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new BusinessException("Khoảng giá không hợp lệ");

        // ── Validate keyword ─────────────────────────────────────────────────
        String kw = null;
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.length() > 100) throw new BusinessException("Từ khóa tối đa 100 ký tự");
            if (!keyword.isEmpty()) {
                // Escape % và _ để tránh LIKE injection
                kw = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            }
        }

        // ── Parse productType ────────────────────────────────────────────────
        ProductType typeEnum = null;
        if (productType != null) {
            try {
                typeEnum = ProductType.valueOf(productType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Tham số truy vấn không hợp lệ");
            }
        }

        // ── Parse status ─────────────────────────────────────────────────────
        ProductStatus statusEnum = null;
        if (status != null) {
            try {
                statusEnum = ProductStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Tham số truy vấn không hợp lệ");
            }
        }

        // ── Resolve sort ─────────────────────────────────────────────────────
        Sort springSort = resolveSort(sort);
        PageRequest pageable = PageRequest.of(page, size, springSort);

        // ── Query ────────────────────────────────────────────────────────────
        Page<Product> productPage = productRepository.findAdminList(
                kw, categoryId, typeEnum, statusEnum, minPrice, maxPrice, pageable);

        return PageResponse.of(productPage.map(this::toAdminResponse));
    }

    @Override
    public ProductResponse getByIdAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại"));
        return toAdminResponse(product);
    }

    // ─── UC14: Cập nhật sản phẩm ─────────────────────────────────────────────

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest req) {

        // 1. Tìm sản phẩm (deleted_at IS NULL qua @SQLRestriction)
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại"));

        // 2. Kiểm tra version khớp trước khi làm bất cứ thứ gì
        //    -> ngăn câu lệnh UPDATE chạy nếu client đang dùng dữ liệu cũ
        if (!req.getVersion().equals(product.getVersion())) {
            throw new DuplicateResourceException("version",
                    "Sản phẩm đã được cập nhật bởi người khác. Vui lòng tải lại");
        }

        // 3. Validate + lấy Category mới
        Category newCategory = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new BusinessException("Danh mục không tồn tại"));

        // 4. Validate thumbnailUrl (nếu có)
        String thumbnailUrl = req.getThumbnailUrl();
        if (thumbnailUrl != null) {
            thumbnailUrl = thumbnailUrl.trim();
            if (!thumbnailUrl.isEmpty() && !URL_PATTERN.matcher(thumbnailUrl).matches()) {
                throw new BusinessException("URL ảnh sản phẩm không hợp lệ");
            }
            if (thumbnailUrl.isEmpty()) thumbnailUrl = null;
        }

        // 5. Validate + chuẩn hoá attributes theo productType GỐC (không đổi type)
        AttributesValidator validator = validatorFactory.getValidator(product.getProductType());
        validator.validate(req.getAttributes());
        Map<String, Object> normalizedAttributes = validator.normalize(req.getAttributes());

        // 6. Chuẩn hoá name, description
        String name        = req.getName().trim();
        String description = req.getDescription() != null ? req.getDescription().trim() : null;
        if (description != null && description.isEmpty()) description = null;

        // 7. Cập nhật các field được phép sửa
        product.setName(name);
        product.setDescription(description);
        product.setBasePrice(req.getBasePrice());
        product.setCategory(newCategory);
        product.setThumbnailUrl(thumbnailUrl);
        product.setAttributes(normalizedAttributes);
        // Không sửa: sku, productType, stockQuantity, reservedQuantity, status

        // 8. Lưu — JPA tự tăng version và kiểm tra trong WHERE
        product = productRepository.save(product);

        log.info("Product updated: id={}, version={}", product.getId(), product.getVersion());
        return toAdminResponse(product);
    }

    // ─── UC15: Ẩn / hiện sản phẩm ────────────────────────────────────────────

    @Override
    @Transactional
    public ProductResponse updateStatus(Long id, String statusStr) {

        // 1. Tìm sản phẩm
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại"));

        // 2. Parse enum — giá trị lạ → 400
        ProductStatus newStatus;
        try {
            newStatus = ProductStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái không hợp lệ");
        }

        // 3. Cập nhật (idempotent: trùng status hiện tại vẫn lưu, trả 200)
        product.setStatus(newStatus);
        productRepository.save(product);

        log.info("Product status updated: id={}, status={}", id, newStatus);

        // 4. Trả DTO nhẹ chỉ gồm id + status (spec UC15 response)
        return ProductResponse.builder()
                .id(product.getId())
                .status(newStatus.name())
                .build();
    }

    // ─── UC16: Xóa mềm sản phẩm ──────────────────────────────────────────────

    @Override
    @Transactional
    public void softDelete(Long id) {

        // 1. Tìm sản phẩm (đã xóa → @SQLRestriction lọc ra → 404)
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại"));

        // 2. Xóa mềm — SKU không đổi (khác với Category)
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Product soft-deleted: id={}, sku={}", id, product.getSku());
    }

    // ─── UC17: Tìm kiếm & lọc sản phẩm (Public) ──────────────────────────────

    @Override
    public PageResponse<ProductResponse> listPublic(
            String keyword, Long categoryId, String productType,
            BigDecimal minPrice, BigDecimal maxPrice,
            String sort, int page, int size) {

        // Validate phân trang
        if (page < 0) throw new BusinessException("Tham số truy vấn không hợp lệ");
        if (size < 1 || size > 50) throw new BusinessException("Tham số truy vấn không hợp lệ");

        // Validate khoảng giá
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Khoảng giá không hợp lệ");
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Khoảng giá không hợp lệ");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new BusinessException("Khoảng giá không hợp lệ");

        // Validate keyword
        String kw = null;
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.length() > 100) throw new BusinessException("Từ khóa tối đa 100 ký tự");
            if (!keyword.isEmpty()) {
                kw = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            }
        }

        // Parse productType
        ProductType typeEnum = null;
        if (productType != null) {
            try {
                typeEnum = ProductType.valueOf(productType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Tham số truy vấn không hợp lệ");
            }
        }

        Sort springSort = resolveSort(sort);
        PageRequest pageable = PageRequest.of(page, size, springSort);

        Page<Product> productPage = productRepository.findPublicList(
                kw, categoryId, typeEnum, minPrice, maxPrice, pageable);

        return PageResponse.of(productPage.map(this::toPublicListResponse));
    }

    // ─── UC18: Xem chi tiết sản phẩm (Public) ────────────────────────────────

    @Override
    public ProductResponse getPublicById(Long id) {
        // findActiveById chỉ trả ACTIVE + not deleted → HIDDEN / deleted → 404 (cùng message)
        Product product = productRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại"));
        return toPublicDetailResponse(product);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────


    /** Map sort string → Spring Sort. Giá trị lạ → 400. */
    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) sort = "newest";
        return switch (sort.toLowerCase()) {
            case "newest"     -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
            case "price_asc"  -> Sort.by(Sort.Direction.ASC,  "basePrice") .and(Sort.by(Sort.Direction.DESC, "id"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "basePrice") .and(Sort.by(Sort.Direction.DESC, "id"));
            default           -> throw new BusinessException("Tham số truy vấn không hợp lệ");
        };
    }

    /**
     * Chuyển entity → DTO Admin đầy đủ (UC12 / UC13 detail / UC14 / UC15).
     */
    public ProductResponse toAdminResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .category(ProductResponse.CategoryRef.builder()
                        .id(p.getCategory().getId())
                        .name(p.getCategory().getName())
                        .build())
                .productType(p.getProductType().name())
                .status(p.getStatus().name())
                .thumbnailUrl(p.getThumbnailUrl())
                .attributes(p.getAttributes())
                .stockQuantity(p.getStockQuantity())
                .version(p.getVersion())
                .createdAt(fmt(p.getCreatedAt()))
                .updatedAt(fmt(p.getUpdatedAt()))
                .build();
    }

    /**
     * UC17 Public list — DTO nhẹ, không lộ sku/stockQuantity/status/version.
     * inStock = stockQuantity > 0.
     */
    private ProductResponse toPublicListResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .basePrice(p.getBasePrice())
                .category(ProductResponse.CategoryRef.builder()
                        .id(p.getCategory().getId())
                        .name(p.getCategory().getName())
                        .build())
                .productType(p.getProductType().name())
                .thumbnailUrl(p.getThumbnailUrl())
                .inStock(p.getStockQuantity() > 0)
                .build();
    }

    /**
     * UC18 Public detail — đầy đủ hơn list nhưng vẫn không lộ sku/status/version.
     * availableQuantity = stockQuantity - reservedQuantity.
     */
    private ProductResponse toPublicDetailResponse(Product p) {
        int available = p.getStockQuantity() - p.getReservedQuantity();
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .category(ProductResponse.CategoryRef.builder()
                        .id(p.getCategory().getId())
                        .name(p.getCategory().getName())
                        .build())
                .productType(p.getProductType().name())
                .thumbnailUrl(p.getThumbnailUrl())
                .attributes(p.getAttributes())
                .inStock(p.getStockQuantity() > 0)
                .availableQuantity(available)
                .createdAt(fmt(p.getCreatedAt()))
                .build();
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(ISO_FMT) : null;
    }
}
