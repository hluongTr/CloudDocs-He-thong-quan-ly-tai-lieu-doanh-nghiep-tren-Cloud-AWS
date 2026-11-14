-- Optional: remove only redundant CREATE_DOCUMENT rows with a successful UPLOAD by the same actor/document.
-- Choose the intended database first. Review the SELECT results before the DELETE.
SELECT c.id,c.document_id,c.user_id,c.created_at FROM audit_logs c
WHERE c.action='CREATE_DOCUMENT' AND EXISTS(SELECT 1 FROM audit_logs u WHERE u.action='UPLOAD' AND u.document_id=c.document_id AND u.user_id=c.user_id);
-- Run this DELETE only after reviewing the rows above. UPLOAD rows are retained for upload confirmation.
SET @clouddocs_previous_safe_updates = @@SESSION.sql_safe_updates;
SET SESSION SQL_SAFE_UPDATES = 0;
START TRANSACTION;
DELETE c FROM audit_logs c JOIN audit_logs u ON u.action='UPLOAD' AND u.document_id=c.document_id AND u.user_id=c.user_id WHERE c.action='CREATE_DOCUMENT';

SELECT ROW_COUNT() AS removed_duplicate_creation_events;
COMMIT;
SET SESSION SQL_SAFE_UPDATES = @clouddocs_previous_safe_updates;
