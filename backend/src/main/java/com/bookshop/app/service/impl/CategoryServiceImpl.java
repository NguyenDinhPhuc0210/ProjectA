package com.bookshop.app.service.impl;

import com.bookshop.app.exception.BusinessException;
import com.bookshop.app.exception.DuplicateResourceException;
import com.bookshop.app.exception.ResourceNotFoundException;
import com.bookshop.app.model.dto.request.CategoryRequest;
import com.bookshop.app.model.dto.response.CategoryResponse;
import com.bookshop.app.model.entity.Category;
import com.bookshop.app.repository.CategoryRepository;
import com.bookshop.app.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CategoryServiceImpl — UC10 + UC11.
 *
 * Quy tắc:
 *  - @SQLRestriction trên Category entity đảm bảo mọi query mặc định lọc deleted_at IS NULL.
 *  - Không bao giờ dùng lệnh DELETE SQL.
 *  - Khi xóa mềm: name → name#deleted_<id> để giải phóng khóa UNIQUE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final CategoryRepository categoryRepository;

    // ─── UC10: Xem danh sách danh mục (Public) ───────────────────────────────

    @Override
    public List<CategoryResponse> listPublic() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(cat -> toResponse(cat, categoryRepository.countActiveProductsByCategoryId(cat.getId()), false))
                .toList();
    }

    // ─── UC11: Quản lý danh mục (Admin) ──────────────────────────────────────

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.getName().trim();
        String description = request.getDescription() != null ? request.getDescription().trim() : null;

        // Validate độ dài sau khi trim
        validateNameLength(name);

        // Kiểm tra trùng name
        if (categoryRepository.existsByName(name)) {
            throw new DuplicateResourceException("name", "Tên danh mục đã tồn tại");
        }

        Category category = Category.builder()
                .name(name)
                .description(description)
                .build();

        try {
            category = categoryRepository.save(category);
        } catch (DataIntegrityViolationException ex) {
            // Bắt race condition: hai request tạo cùng name đồng thời
            if (isDuplicateNameConstraint(ex)) {
                throw new DuplicateResourceException("name", "Tên danh mục đã tồn tại");
            }
            throw ex;
        }

        log.info("Category created: id={}, name={}", category.getId(), name);
        return toAdminResponse(category);
    }

    @Override
    public List<CategoryResponse> listAdmin() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                // Admin thấy tất cả sản phẩm (kể cả HIDDEN) → dùng countAll
                .map(cat -> toResponse(cat, categoryRepository.countAllProductsByCategoryId(cat.getId()), true))
                .toList();
    }

    @Override
    public CategoryResponse getById(Long id) {
        Category category = findActiveById(id);
        long count = categoryRepository.countAllProductsByCategoryId(id);
        return toResponse(category, count, true);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findActiveById(id);

        String name = request.getName().trim();
        String description = request.getDescription() != null ? request.getDescription().trim() : null;

        validateNameLength(name);

        // Kiểm tra trùng — loại trừ chính danh mục đang sửa
        if (categoryRepository.existsByNameExcludingId(name, id)) {
            throw new DuplicateResourceException("name", "Tên danh mục đã tồn tại");
        }

        category.setName(name);
        category.setDescription(description);

        try {
            category = categoryRepository.save(category);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateNameConstraint(ex)) {
                throw new DuplicateResourceException("name", "Tên danh mục đã tồn tại");
            }
            throw ex;
        }

        log.info("Category updated: id={}, newName={}", id, name);
        return toAdminResponse(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = findActiveById(id);

        // Từ chối nếu còn sản phẩm chưa xóa (gồm cả HIDDEN)
        long productCount = categoryRepository.countAllProductsByCategoryId(id);
        if (productCount > 0) {
            throw new BusinessException("Không thể xóa danh mục đang có sản phẩm");
        }

        // Xóa mềm: đổi name để giải phóng UNIQUE, đặt deleted_at
        category.setName(category.getName() + "#deleted_" + id);
        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);

        log.info("Category soft-deleted: id={}", id);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Category findActiveById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại"));
    }

    private void validateNameLength(String name) {
        if (name.length() < 2 || name.length() > 100) {
            throw new BusinessException("Tên danh mục phải từ 2 đến 100 ký tự");
        }
    }

    private boolean isDuplicateNameConstraint(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("uk_categories_name");
    }

    /**
     * Chuyển entity → DTO (public).
     */
    private CategoryResponse toResponse(Category cat, long productCount, boolean includeTimestamps) {
        CategoryResponse.CategoryResponseBuilder builder = CategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .description(cat.getDescription())
                .productCount(productCount);

        if (includeTimestamps && cat.getCreatedAt() != null) {
            builder.createdAt(cat.getCreatedAt().format(ISO_FMT));
            builder.updatedAt(cat.getUpdatedAt().format(ISO_FMT));
        }

        return builder.build();
    }

    /**
     * Chuyển entity → DTO đầy đủ (Admin response bao gồm timestamps).
     */
    private CategoryResponse toAdminResponse(Category cat) {
        long count = categoryRepository.countAllProductsByCategoryId(cat.getId());
        return toResponse(cat, count, true);
    }
}
