USE company_documents;

-- Run this file FIRST. Do not continue to the apply script if the second query returns rows.

SELECT COUNT(*) AS documents_total
FROM documents;

SELECT COUNT(*) AS users_total
FROM users;

-- Every current documents.owner must match an existing users.username.
SELECT
    d.id,
    d.owner
FROM documents d
LEFT JOIN users u ON LOWER(u.username) = LOWER(d.owner)
WHERE d.owner IS NULL
    OR u.id IS NULL;

-- Expected result: 0 rows.
