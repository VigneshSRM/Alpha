-- The modules an account switches on, one row per module.
CREATE TABLE business_module (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    module_code VARCHAR(20) NOT NULL,
    selected_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_business_module UNIQUE (user_id, module_code),
    CONSTRAINT fk_business_module_user FOREIGN KEY (user_id)
        REFERENCES portal_user (id) ON DELETE CASCADE
);
