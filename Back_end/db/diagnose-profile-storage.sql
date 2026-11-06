-- Read-only diagnosis. Run manually in the same schema used by the backend.
-- No S3 requests; no ownership, profile, role or status updates.
USE company_documents;

SELECT u.id, u.username, u.role, u.status,
       CASE WHEN u.email LIKE '%@identity.invalid' THEN 'PLACEHOLDER' ELSE 'CONTACT_OR_EMPTY' END AS email_kind,
       c.issuer, c.subject
FROM users u LEFT JOIN cognito_identities c ON c.user_id = u.id
ORDER BY u.id;

SELECT d.owner_id, u.username, COUNT(*) AS document_count,
       SUM(CASE WHEN d.file_size >= 0 THEN d.file_size ELSE 0 END) AS allocated_known_bytes,
       SUM(CASE WHEN d.file_size IS NULL OR d.file_size < 0 THEN 1 ELSE 0 END) AS missing_size_count,
       SUM(CASE WHEN d.file_size = 0 THEN 1 ELSE 0 END) AS zero_size_count,
       SUM(CASE WHEN d.file_size >= 0 AND EXISTS (
           SELECT 1 FROM audit_logs a WHERE a.document_id = d.id AND a.action = 'UPLOAD'
       ) THEN d.file_size ELSE 0 END) AS upload_confirmed_known_bytes
FROM documents d LEFT JOIN users u ON u.id = d.owner_id
GROUP BY d.owner_id, u.username
ORDER BY d.owner_id;

-- Metadata anomalies to review separately; never reassign ownership to fix statistics.
SELECT d.id, d.owner_id, d.file_size
FROM documents d LEFT JOIN users u ON u.id = d.owner_id
WHERE d.file_size IS NULL OR d.file_size < 0 OR d.file_size = 0 OR u.id IS NULL;
