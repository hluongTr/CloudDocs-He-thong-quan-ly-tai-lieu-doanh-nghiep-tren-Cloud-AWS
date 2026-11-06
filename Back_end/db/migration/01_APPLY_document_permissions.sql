USE company_documents;

-- Run this file only after 00_PRECHECK.sql returns 0 owner mismatches.

-- 1. Add normalized owner reference.
ALTER TABLE documents
    ADD COLUMN owner_id BIGINT NULL;

-- 2. Backfill from the existing owner username.
UPDATE documents d
JOIN users u ON LOWER(u.username) = LOWER(d.owner)
SET d.owner_id = u.id
WHERE d.owner_id IS NULL;

-- 3. Safety check. Expected result: 0 rows.
SELECT
    id,
    owner
FROM documents
WHERE owner_id IS NULL;

-- STOP if the previous query returned any rows.

-- 4. Make normalized ownership mandatory and protected by a foreign key.
ALTER TABLE documents
    MODIFY COLUMN owner_id BIGINT NOT NULL,
    ADD INDEX idx_documents_owner_id (owner_id),
    ADD CONSTRAINT fk_documents_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
        ON DELETE RESTRICT;

-- 5. Create document permission records.
CREATE TABLE document_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT uk_document_permission_user
        UNIQUE (document_id, user_id),

    CONSTRAINT fk_document_permissions_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_document_permissions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_document_permission
        CHECK (permission IN ('VIEW', 'DOWNLOAD', 'EDIT'))
);

CREATE INDEX idx_document_permissions_user
    ON document_permissions(user_id);
CREATE INDEX idx_document_permissions_document
    ON document_permissions(document_id);

-- 6. Final verification.
SELECT
    id,
    owner,
    owner_id
FROM documents
ORDER BY id;

SHOW COLUMNS FROM document_permissions;
