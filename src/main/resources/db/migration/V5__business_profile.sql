-- The business profile captured right after registration.
CREATE TABLE business_profile (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT       NOT NULL,
    vertical               VARCHAR(20)  NOT NULL,
    business_name          VARCHAR(120) NOT NULL,
    phone                  VARCHAR(20)  NOT NULL,
    business_type          VARCHAR(60)  NOT NULL,
    country                CHAR(2)      NOT NULL,
    state                  VARCHAR(80)  NOT NULL,
    gstin                  VARCHAR(15)  NULL,
    address_line1          VARCHAR(160) NOT NULL,
    address_line2          VARCHAR(160) NULL,
    city                   VARCHAR(80)  NOT NULL,
    pincode                VARCHAR(10)  NOT NULL,
    language               VARCHAR(20)  NOT NULL,
    time_zone              VARCHAR(40)  NOT NULL,
    currency               CHAR(3)      NOT NULL,
    fiscal_year_start      INT          NOT NULL,
    inventory_start_date   DATE         NOT NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_business_profile_user UNIQUE (user_id),
    CONSTRAINT fk_business_profile_user FOREIGN KEY (user_id)
        REFERENCES portal_user (id) ON DELETE CASCADE
);
