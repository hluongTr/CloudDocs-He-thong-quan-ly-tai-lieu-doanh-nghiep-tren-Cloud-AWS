# CloudDocs backend

Java 21 / Spring Boot 3.5.6 / MySQL 8 / Cognito access tokens / private S3.
See [project setup](../README.md) for the current startup procedure.

## Database

The application validates its schema; it does not execute migrations automatically.
The repository contains incremental migrations for an existing business schema, not a complete
empty-database installer. Existing users, documents and audit_logs tables are prerequisites.
For a new machine, export/import the actual schema first, then review which migrations apply.
Never rerun historical ALTER/CREATE scripts against tables already migrated.

| Order | File | Purpose |
| --- | --- | --- |
| 0 | db/migration/00_PRECHECK.sql | Verify legacy owner references |
| 1 | db/migration/01_APPLY_document_permissions.sql | Normalized ownership and sharing |
| 2 | db/migration/02_APPLY_document_favorites.sql | Favorites |
| 3 | db/migration/03_APPLY_notifications.sql | Announcements |
| 4 | db/migration/04_APPLY_cognito_identities.sql | Issuer/sub identity mapping |
| 5 | db/migration/05_APPLY_optional_contact_email.sql | Nullable contact email |
| 6 | db/migration/06_APPLY_work_tasks.sql | Work tasks and recipient-scoped notifications |

Migration 01 must stop if its precheck returns mismatched owners. Migration 04 and
share-cognito-identities.sql are alternative ways to create the same identity table, not two
scripts to run consecutively. SQL templates are manual tools and may select company_documents;
review the active connection/schema before executing them.

## Current API

Every business request requires a Cognito access token. Actor, role, status and owner are
resolved by the backend; the client cannot choose an owner during metadata creation.

| API | Behavior |
| --- | --- |
| GET /api/health | Public health check |
| GET /api/auth/me | Current profile |
| PUT /api/auth/me | Contact profile |
| PUT /api/auth/me/display-name | Name only |
| POST /api/auth/logout | Record logout |
| GET/POST /api/documents | Authorized listing / metadata allocation |
| GET/PUT/DELETE /api/documents/{id} | Detail / edit / move to trash |
| GET /api/documents/shared, /recent, /trash | Scoped collections |
| POST /api/documents/{id}/restore | Restore to ACTIVE with prior shares |
| DELETE /api/documents/{id}/permanent | Delete stored object; retain audit metadata |
| POST /api/documents/{id}/upload-url | Reserve/check owner quota and sign PUT |
| POST /api/documents/{id}/upload-complete | HEAD object and confirm actual size |
| GET /api/documents/{id}/download-url, /preview-url | Access-checked signed GET |
| GET/PUT/DELETE /api/documents/{id}/permissions/... | Owner/admin sharing management |
| GET/PUT/DELETE /api/favorites/... | Current user's favorites |
| GET/POST/DELETE /api/notifications/... | Read / admin create / admin delete |
| GET /api/audit-logs, /recent | ADMIN: all actors; USER: own actor only |
| GET /api/storage/usage | Allocated personal quota |
| GET /api/dashboard/summary | Dashboard and storage distribution |
| GET /api/departments | Department options |
| GET/PATCH/DELETE /api/users/... | Admin account management |

Upload sequence: POST metadata -> POST upload-url -> PUT File to S3 -> POST upload-complete.
A failed upload can leave a pending allocation: it is still charged until permanently deleted.
New storage keys use UUIDs; original filenames stay in metadata. Input sizes/lengths and type
labels are validated before reaching storage writes.

OWNER/ADMIN can manage shares and trash. EDIT can view/download/edit. DOWNLOAD can view/download.
VIEW can only view. TRASHED/PURGED documents block ordinary operations for everyone.
Ownership is checked by owner_id, not the display name or historical owner text.

## Work tasks

See [workflow and API](../WORK_TASKS.md). Migration 06 is required before starting the new
backend. /api/work-tasks checks actor, participant and creator permissions on every call.
Notification listing now includes global rows plus only the authenticated recipient's private
rows; permission to dismiss a notification is enforced by the service.

## Account deletion and history

An account with active/trashed documents or unfinished created/assigned tasks cannot be deleted. A removable account is marked
DELETED and hidden from the user list; its Cognito mapping and historical references remain.
Its token no longer grants access and it cannot automatically reprovision on the next login.
This does not delete the external Cognito user. The last active admin and the current admin
remain protected from deletion/demotion/locking.

## Compatibility

/file, /download and /preview local endpoints are retained for existing local-storage clients.
The current web UI uses S3 URLs. Do not remove stored files or legacy schema/password columns
without a separate migration; they are not active password authentication.

See [storage details](../STORAGE_QUOTAS.md) and [verification report](../PROJECT_REVIEW.md).
