# CloudDocs storage accounting

USER quota defaults to 100 GB, ADMIN to 1 TB, using decimal units. These are per-account
limits, not the capacity of a shared S3 bucket. The file limit defaults to 104,857,600 bytes.
Configuration: STORAGE_USER_QUOTA_BYTES, STORAGE_ADMIN_QUOTA_BYTES and the max-file property.

## Personal quota

Allocation is the sum of known documents.file_size for the owner. It includes pending uploads
and trash, excludes PURGED metadata and never charges a recipient for another owner's share.
The sidebar shows allocated bytes / quota above the account/logout footer. Missing sizes are
shown as a lower bound. Zero bytes is a valid size, not evidence of a missing S3 object.

A move to trash retains the object, permissions and allocation. Permanent deletion removes
the stored object and sharing rows, sets metadata to PURGED with size 0, and retains its audit
reference. Restore returns to ACTIVE; prior sharing permissions become usable again.

## Distribution chart

ADMIN sees all active documents across accounts. USER sees only their own active documents.
Each recorded format remains in the legend, including zero-byte formats. Files with unknown
sizes are counted in a note rather than assigned a fabricated size. The chart excludes trash,
so it can differ from quota allocation. It is not overwritten by a personal-quota refresh.

Both statistics are based on database metadata. They are not a physical S3 inventory and do
not include bucket object versions or orphan objects without metadata. Confirmation fields
remain available in the usage API for diagnostics; lack of UPLOAD audit is not proof of loss.

## Upload and concurrency

The owner row serializes capacity reservations. Signing and upload completion re-read the
locked document after the quota check, so a stale upload cannot overwrite a trash/purge state.
Completion checks actual S3 size with HEAD and validates capacity again. Upload audits wait
for the business commit to avoid nested audit transactions waiting on the quota lock.
New keys use UUIDs, avoiding simultaneous filename/timestamp collisions.

S3 and MySQL do not share a distributed transaction. An S3 delete can succeed before a later
local/DB operation fails; recovery and simultaneous upload/purge scenarios need validation
against the real environment. Previously issued signed URLs remain valid until their expiry.
S3 Versioning, if enabled, may retain old versions after DeleteObject.

## Verification

Automated tests cover capacity boundaries, pending/zero/unknown sizes, owner scope, chart
formats, stale upload status changes, failed signing/completion, trash/restore/purge rules,
and account roles. They use mocked storage/repositories and do not establish end-to-end
correctness for the deployed MySQL/Cognito/S3 environment.

Live checks: upload a small TXT/PDF/image, compare metadata and chart; share with another
account; trash and restore; permanently delete; test size/quota boundaries with temporary
small test limits; attempt two simultaneous uploads when only one fits. Use disposable test
files and a separate test environment. No cleanup script should delete the user's real files.
