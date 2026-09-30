-- Flyway Migration: V1 - Initialize users table
-- Managed by Flyway, never run manually.

CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    email               VARCHAR(255)    NOT NULL,
    password            VARCHAR(100)    NULL,
    full_name           VARCHAR(100)    NOT NULL,
    phone_number        VARCHAR(15)     NULL,
    avatar_key          VARCHAR(50)     NOT NULL DEFAULT 'default',
    role                ENUM('ROLE_CUSTOMER','ROLE_ADMIN') NOT NULL,
    account_status      ENUM('ACTIVE','LOCKED')            NOT NULL DEFAULT 'ACTIVE',
    auth_provider       ENUM('LOCAL','GOOGLE')             NOT NULL DEFAULT 'LOCAL',
    reset_token         CHAR(64)        NULL,
    reset_token_exp     DATETIME        NULL,
    password_changed_at DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          DATETIME        NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email         (email),
    UNIQUE KEY uq_users_phone_number  (phone_number),
    INDEX      idx_users_reset_token  (reset_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
