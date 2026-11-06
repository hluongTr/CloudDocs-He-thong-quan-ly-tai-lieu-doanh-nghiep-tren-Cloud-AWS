-- Review and apply manually to the intended schema. The app uses ddl-auto=validate.
-- Existing users, password columns, documents, and historical migrations stay untouched.
CREATE TABLE cognito_identities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issuer VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uk_cognito_identity UNIQUE (issuer, subject),
    CONSTRAINT fk_cognito_identities_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
