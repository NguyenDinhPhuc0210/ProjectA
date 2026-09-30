-- Flyway Migration: V2 - Catalog (Categories + Products)
-- Sprint 2: Book & Hobby Retailer
-- Managed by Flyway, never run manually.

-- ─── 1. categories ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(160)    NOT NULL,           -- 160 để chứa hậu tố #deleted_<id>
    description VARCHAR(500)    NULL,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL,
    deleted_at  DATETIME        NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── 2. products ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS products (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    sku               VARCHAR(30)     NOT NULL,
    name              VARCHAR(255)    NOT NULL,
    description       TEXT            NULL,
    base_price        DECIMAL(15,0)   NOT NULL,
    category_id       BIGINT          NOT NULL,
    product_type      ENUM('BOOK','HOBBY')    NOT NULL,
    status            ENUM('ACTIVE','HIDDEN') NOT NULL DEFAULT 'ACTIVE',
    thumbnail_url     VARCHAR(500)    NULL,
    attributes        JSON            NOT NULL,
    stock_quantity    INT             NOT NULL DEFAULT 0,
    reserved_quantity INT             NOT NULL DEFAULT 0,
    version           BIGINT          NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL,
    updated_at        DATETIME        NOT NULL,
    deleted_at        DATETIME        NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku      UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT chk_products_price    CHECK (base_price > 0),
    CONSTRAINT chk_products_stock    CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= stock_quantity),
    CONSTRAINT chk_products_attrs    CHECK (JSON_TYPE(attributes) = 'OBJECT'),

    INDEX idx_products_list    (deleted_at, status, category_id),
    INDEX idx_products_price   (deleted_at, base_price),
    INDEX idx_products_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
