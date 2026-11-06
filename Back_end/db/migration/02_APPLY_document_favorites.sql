USE company_documents;

CREATE TABLE IF NOT EXISTS document_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT uk_document_favorite_user
        UNIQUE (user_id, document_id),
    CONSTRAINT fk_document_favorites_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_favorites_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_document_favorites_user
    ON document_favorites(user_id);
CREATE INDEX idx_document_favorites_document
    ON document_favorites(document_id);

SHOW COLUMNS FROM document_favorites;
