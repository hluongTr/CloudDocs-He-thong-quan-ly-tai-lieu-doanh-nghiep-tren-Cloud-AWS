# CloudDocs

Enterprise document management with Java 21 / Spring Boot 3.5.6, MySQL, vanilla HTML/CSS/JS,
Cognito Authorization Code + PKCE and private S3 signed URLs.

## Run the existing project

1. Use the existing business schema. Review the migration history before applying missing
   scripts in Back_end/db/migration; the application uses ddl-auto=validate and never migrates
   automatically. The repository does not yet include an empty-database base-schema installer.
2. Build Back_end with Java 21: mvn -f Back_end/pom.xml package.
3. From the project root run scripts/start-local-backend.ps1. It prompts for the MySQL password,
   sets the public development Cognito issuer/client and derives CORS from the frontend callback.
   DB host/port/name/user are script parameters. S3 credentials come from the normal AWS chain.
4. Run scripts/start-frontend.ps1 (or node scripts/serve-frontend.cjs). Open the printed URL.
   The frontend server derives its host, port and path from FrontEnd/js/cognito-config.js.
   It exposes only FrontEnd, including the configured /FrontEnd path alias if present.
5. Stop and restart the backend after a new build; refresh the browser after frontend changes.
   An already running Java process does not automatically reload a rebuilt JAR.

The public callback/logout URLs in cognito-config.js must exactly match the app client's
allowed URLs. For a different pool, configure COGNITO_ISSUER_URI, COGNITO_CLIENT_ID and
COGNITO_USER_INFO_URI together. Do not put client secrets, AWS credentials or tokens in source.
For deployed environments use the intended database, HTTPS, CORS origin and IAM role;
local example defaults are not production configuration.

## Identity, names and access

Identity links use issuer + sub, never email. New accounts become USER; existing role/status
are read from MySQL on each request. Bootstrap the first admin with the reviewed template
Back_end/db/bootstrap-cognito-admin.sql; it defaults to NULL targets and ROLLBACK.

Profile Họ và tên is used for notification authors, activity actors and document owners in all
collections. Ownership checks use owner_id. The historical owner text and password columns
remain for schema compatibility, not for choosing an account or signing in with a local password.
Only ACTIVE accounts with ADMIN/USER roles can access business APIs.

ADMIN sees system documents and global audit history. USER sees owned/shared documents and
only their own actor audit events. OWNER/ADMIN manage shares and trash; EDIT edits/downloads;
DOWNLOAD downloads; VIEW only previews. Deletion marks a removable account DELETED so it is
hidden and denied access while retaining identity/audit references. It does not remove the user
from AWS Cognito. Accounts owning active/trash documents cannot be deleted.

## Documents, trash and bulk actions

Upload: metadata -> signed PUT URL -> PUT file to S3 -> upload-complete. Failed uploads may
leave an allocation that can be moved to trash and permanently deleted. UUID object keys keep
simultaneous same-name uploads distinct and avoid long encoded filename keys.

DELETE /api/documents/{id} moves to TRASHED and retains the file and shares. Trash blocks
preview/download/edit/share. Restore returns to ACTIVE and keeps prior shares. Permanent delete
removes the stored object and permissions, sets metadata PURGED with size 0, and preserves audit
references. Only owner/admin can manage trash. No automatic empty-trash job is configured.

The Documents table supports selecting visible rows, bulk trash, one-recipient/many-document
sharing and separate-file downloads. Each request rechecks access. Partial failures remain
selected and batches stop when the login session changes. Browsers may ask to allow multiple
file downloads. Zero-byte files are valid downloads/previews.

Global announcements can be created/deleted by ADMIN; deletion affects all accounts.
Work-task notifications are private to their recipient, who can dismiss them. CREATE/EDIT/SHARE/DELETE/RESTORE/PURGE and announcement actions are represented in
audit logs. Metadata edits, sharing and announcement mutations write their audit in the same
transaction. Upload audit is written after commit to avoid nested quota-lock waits.

## Work assignment and reports

The Công việc page supports one-person assignments, deadlines, progress, document reports,
creator approval/change requests, private notifications and audit events. Apply migration 06
before starting this backend. See [setup and workflow](WORK_TASKS.md) for the exact migration
steps, permissions and live test sequence. Existing global notifications remain global.

## Storage

The sidebar above the account footer shows allocated personal bytes / quota, including pending
uploads and trash. USER defaults to 100 GB, ADMIN to 1 TB. Shares charge only the owner.
The distribution chart is separate: ADMIN sees all active system documents; USER sees only
owned active documents. All recorded formats appear, including 0 B; unknown sizes are explicit.
These are database metadata allocations, not a complete physical S3 inventory or version count.
See [storage details](STORAGE_QUOTAS.md).

## Verification and documentation

- Backend: mvn -f Back_end/pom.xml test
- Frontend: node --test FrontEnd/tests/*.test.cjs
- PowerShell scripts can be syntax-checked without running their external operations.
- [Review and remaining live checks](PROJECT_REVIEW.md)
- [Backend APIs and migration order](Back_end/README.md)
- [Frontend server and configuration](FrontEnd/README.md)
- [S3 infrastructure templates](Back_end/aws/s3/README.md)

Automated tests use mock repositories and AWS clients. They do not prove real database migrations,
row-lock concurrency, Cognito redirects/UserInfo, S3 CORS/object transfers or browser PDF rendering.
No review step executes SQL migrations, changes AWS policies or deletes uploaded user files.
