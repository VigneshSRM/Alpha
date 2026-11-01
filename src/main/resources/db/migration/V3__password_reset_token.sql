-- Single-use reset tokens. Only the SHA-256 of the token is stored, so a dump
-- of this table cannot be replayed.
CREATE TABLE password_reset_token (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    token_hash CHAR(64)  NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP NULL,
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uq_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id)
        REFERENCES portal_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_reset_token_user ON password_reset_token (user_id);
