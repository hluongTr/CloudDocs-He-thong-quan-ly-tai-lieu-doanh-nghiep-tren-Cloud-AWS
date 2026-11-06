-- Apply manually after reviewing the target schema. No user rows are modified.
-- Review SHOW FULL COLUMNS FROM users LIKE 'email' first; preserve a custom
-- column charset/collation in the ALTER if it differs from the table default.
-- Cognito identities can have no verified contact email; do not invent fake emails.
ALTER TABLE users MODIFY COLUMN email VARCHAR(150) NULL;
-- Existing UNIQUE constraint stays in place. Historical password/identity links stay intact.
