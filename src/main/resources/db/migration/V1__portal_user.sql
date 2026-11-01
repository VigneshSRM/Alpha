-- Portal accounts. Passwords are BCrypt hashes (strength from alpha.security.bcrypt-strength).
CREATE TABLE portal_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(80)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP    NULL,
    CONSTRAINT pk_portal_user PRIMARY KEY (id),
    CONSTRAINT uq_portal_user_email UNIQUE (email)
);

CREATE INDEX ix_portal_user_created ON portal_user (created_at);
