-- Manual template only. Verify identity and numeric user_id after the first Cognito login.
SET @target_user_id = NULL;
SET @trusted_issuer = NULL;
SET @verified_subject = NULL;
START TRANSACTION;
SELECT u.id, u.username, u.role, u.status, c.issuer, c.subject
FROM users u JOIN cognito_identities c ON c.user_id = u.id
WHERE u.id = @target_user_id AND BINARY c.issuer = BINARY @trusted_issuer
  AND BINARY c.subject = BINARY @verified_subject FOR UPDATE;
UPDATE users u JOIN cognito_identities c ON c.user_id = u.id
SET u.role = 'ADMIN'
WHERE u.id = @target_user_id AND u.status = 'ACTIVE'
  AND BINARY c.issuer = BINARY @trusted_issuer AND BINARY c.subject = BINARY @verified_subject;
SELECT ROW_COUNT() AS changed_rows;
-- Review the exact selected profile; use COMMIT only when deliberately granting ADMIN.
ROLLBACK;
