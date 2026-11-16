-- Run once after migrations 01–07. Existing documents remain at the root.
USE company_documents;

CREATE TABLE document_folders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    name VARCHAR(120) NOT NULL,
    INDEX idx_folder_owner_parent (owner_id, parent_id),
    CONSTRAINT fk_folder_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES document_folders(id)
) ENGINE=InnoDB;

ALTER TABLE documents ADD COLUMN folder_id BIGINT NULL,
    ADD INDEX idx_document_folder (folder_id),
    ADD CONSTRAINT fk_document_folder FOREIGN KEY (folder_id) REFERENCES document_folders(id);
