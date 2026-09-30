package com.bookshop.app.service.validator;

import com.bookshop.app.model.enums.ProductType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory chọn AttributesValidator theo ProductType.
 * Mở rộng loại mới: thêm case vào switch — không sửa code cũ.
 */
@Component
@RequiredArgsConstructor
public class AttributesValidatorFactory {

    private final BookAttributesValidator  bookValidator;
    private final HobbyAttributesValidator hobbyValidator;

    public AttributesValidator getValidator(ProductType productType) {
        return switch (productType) {
            case BOOK  -> bookValidator;
            case HOBBY -> hobbyValidator;
        };
    }
}
