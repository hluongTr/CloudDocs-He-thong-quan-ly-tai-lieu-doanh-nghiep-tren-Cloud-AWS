-- CloudDocs / CloudDocs_newupdate: cognito_identities matches CognitoIdentity.java.
-- This file is for manual review. It does not choose a database or grant ADMIN.
-- Confirm the connection host in your SQL client: local MySQL or intended RDS.

-- STEP 1: Run ONLY these checks first in the intended schema.
SELECT DATABASE() AS selected_database, @@hostname AS server_hostname,
       @@port AS server_port, VERSION() AS mysql_version;
SHOW CREATE TABLE users;
SHOW TABLES LIKE 'cognito_identities';

-- STEP 2: Run this CREATE only after confirming the target database,
-- users.id is BIGINT signed and users uses InnoDB, and the new table is absent.
-- If the table exists, inspect SHOW CREATE TABLE cognito_identities instead.
-- MySQL DDL commits implicitly; do not rely on ROLLBACK to undo CREATE TABLE.
CREATE TABLE cognito_identities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issuer VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uk_cognito_identity UNIQUE (issuer, subject),
    CONSTRAINT fk_cognito_identities_user_id
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- STEP 3: Inspect the resulting structure.
SHOW CREATE TABLE cognito_identities;
