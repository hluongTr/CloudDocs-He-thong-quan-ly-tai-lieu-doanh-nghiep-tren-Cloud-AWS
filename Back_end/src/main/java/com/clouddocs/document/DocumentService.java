package com.clouddocs.document;

import com.clouddocs.document.DocumentDtos.*;
import com.clouddocs.storage.S3StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import com.clouddocs.audit.AuditLogService;

import java.util.List;
import java.util.LinkedHashMap;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final S3StorageService storageService;
    private final com.clouddocs.storage.LocalFileStorageService localFileStorage;
    private final com.clouddocs.auth.UserRepository users;
    private final com.clouddocs.permission.DocumentPermissionService permissionService;
    private final String bucket;
    private final com.clouddocs.work.WorkTaskRepository workTasks;
    private final AuditLogService auditLogService;
    private final com.clouddocs.audit.AuditLogRepository auditLogs;
    private final com.clouddocs.storage.StorageQuotaService quotas;

    public DocumentService(
            DocumentRepository repository,
            S3StorageService storageService,
            com.clouddocs.storage.LocalFileStorageService localFileStorage,
            com.clouddocs.auth.UserRepository users,
            com.clouddocs.permission.DocumentPermissionService permissionService,
            AuditLogService auditLogService,
            com.clouddocs.audit.AuditLogRepository auditLogs,
            com.clouddocs.storage.StorageQuotaService quotas,
            com.clouddocs.work.WorkTaskRepository workTasks,
            @Value("${aws.s3.bucket}") String bucket) {

        this.repository = repository;
        this.storageService = storageService;
        this.localFileStorage = localFileStorage;
        this.users = users;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
        this.auditLogs = auditLogs;
        this.bucket = bucket;
        this.quotas = quotas;
        this.workTasks = workTasks;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findShared(String username) {
        com.clouddocs.auth.User user = currentUser(username);
        return toResponses(repository.findSharedByUserId(user.getId()).stream()
                .filter(Document::isAvailable).toList(), user);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findRecent(String username) {
        com.clouddocs.auth.User user = currentUser(username);
        List<String> actions =
                List.of("VIEW", "DOWNLOAD", "EDIT", "SHARE", "UPLOAD", "CREATE_DOCUMENT", "RESTORE");
        LinkedHashMap<Long, Document> unique = new LinkedHashMap<>();
        auditLogs
                .findRecentDocumentActivity(user.getId(), actions)
                .forEach(
                        log -> {
                            Document document = log.getDocument();
                            if (document != null
                                    && permissionService.canView(
                                            document, username, isAdmin(user))) {
                                unique.putIfAbsent(document.getId(), document);
                            }
                        });
        return toResponses(unique.values().stream().limit(50).toList(), user);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll(
            String q, String type, String department, String username) {
        com.clouddocs.auth.User user = currentUser(username);
        List<Document> source =
                isAdmin(user)
                        ? repository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                        : repository.findAccessibleByUserId(user.getId());

        var ownerNames = DocumentOwnerNames.resolve(source, users);
        return source.stream()
                .filter(Document::isAvailable)
                .filter(
                        d ->
                                q == null
                                        || q.isBlank()
                                        || contains(d.getTitle(), q)
                                        || contains(d.getFileName(), q)
                                        || contains(DocumentOwnerNames.name(d, ownerNames), q)
                                        || contains(d.getDepartment(), q))
                .filter(
                        d ->
                                type == null
                                        || type.isBlank()
                                        || d.getFileType().equalsIgnoreCase(type))
                .filter(
                        d ->
                                department == null
                                        || department.isBlank()
                                        || d.getDepartment().equalsIgnoreCase(department))
                .map(d -> toResponse(d, user, DocumentOwnerNames.name(d, ownerNames)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(Long id, String username) {

        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);

        requireView(d, user);
        return toResponse(d, user);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DocumentResponse create(CreateDocumentRequest request, String owner) {
        com.clouddocs.auth.User user = currentUser(owner);
        quotas.requireCapacity(user.getId(), null, request.fileSize());

        Document d = new Document();

        d.setTitle(request.title());
        d.setDescription(request.description());
        d.setFileName(request.fileName());
        d.setFileType(request.fileType().toUpperCase(java.util.Locale.ROOT));
        d.setFileSize(request.fileSize());
        d.setOwner(user.getUsername());
        d.setOwnerId(user.getId());
        d.setDepartment(request.department());
        d.setStatus("ACTIVE");

        d.setS3Key("documents/" + java.util.UUID.randomUUID());

        // Lưu tài liệu vào database
        Document savedDocument = repository.save(d);

        // Ghi Audit Log sau khi tạo tài liệu thành công
        logUploadAfterCommit(
                owner,
                "CREATE_DOCUMENT",
                savedDocument.getId(),
                null,
                owner + " tạo tài liệu " + savedDocument.getTitle());

        return toResponse(savedDocument, user);
    }

    @Transactional
    public DocumentResponse update(Long id, UpdateDocumentRequest request, String username) {

        Document d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        com.clouddocs.auth.User user = currentUser(username);
        requireEdit(d, user);

        d.setTitle(request.title());
        d.setDescription(request.description());
        d.setDepartment(request.department());

        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("ACTIVE", "SHARED", "ARCHIVED").contains(status))
                throw new IllegalArgumentException("Trạng thái tài liệu không hợp lệ");
            d.setStatus(status);
        }

        Document savedDocument = repository.save(d);

        // Ghi Audit Log sau khi chỉnh sửa thành công
        auditLogService.logInTransaction(
                username,
                "EDIT",
                id,
                null,
                username + " chỉnh sửa tài liệu " + savedDocument.getTitle());

        return toResponse(savedDocument, user);
    }

    @Transactional
    public void delete(Long id, String username) {

        Document d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        com.clouddocs.auth.User user = currentUser(username);

        // 1. Kiểm tra quyền xóa
        requireOwnerOrAdmin(d, user);

        if (!d.isAvailable()) throw new IllegalArgumentException("Tài liệu không còn trong kho");
        requireReportMutable(d);
        d.setStatus("TRASHED");
        auditLogService.logInTransaction(username, "DELETE", id, null,
                com.clouddocs.auth.ProfilePresentation.name(user) + " đã chuyển tài liệu vào thùng rác: " + d.getTitle());
        repository.save(d);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findTrash(String username) {
        var user = currentUser(username);
        return toResponses((isAdmin(user) ? repository.findAll() : repository.findByOwnerId(user.getId()))
                .stream().filter(Document::isTrashed)
                .sorted(java.util.Comparator.comparing(Document::getUpdatedAt).reversed()).toList(), user);
    }

    @Transactional
    public void restore(Long id, String username) {
        Document d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        var user = currentUser(username);
        requireOwnerOrAdmin(d, user);
        if (!d.isTrashed()) throw new IllegalArgumentException("Tài liệu không nằm trong thùng rác");
        d.setStatus("ACTIVE");
        auditLogService.logInTransaction(username, "RESTORE", id, null,
                com.clouddocs.auth.ProfilePresentation.name(user) + " đã khôi phục tài liệu: " + d.getTitle());
        repository.save(d);
    }

    @Transactional
    public void purge(Long id, String username) {
        Document d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        var user = currentUser(username);
        requireOwnerOrAdmin(d, user);
        if (!d.isTrashed()) throw new IllegalArgumentException("Chỉ xóa vĩnh viễn tài liệu trong thùng rác");
        requireReportMutable(d);
        storageService.deleteIfExists(bucket, d.getS3Key());
        localFileStorage.deleteIfExists(d.getS3Key());
        permissionService.deleteAllForDocument(id);
        auditLogService.logInTransaction(username, "PURGE", id, null,
                com.clouddocs.auth.ProfilePresentation.name(user) + " đã xóa vĩnh viễn tài liệu: " + d.getTitle());
        // Retain the metadata row so historical audit foreign keys and document names survive.
        d.setStatus("PURGED");
        d.setFileSize(0L);
        repository.save(d);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void uploadLocalFile(Long id, MultipartFile file, String username) {

        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);
        requireEdit(d, user);

        quotas.requireCapacity(d.getOwnerId(), d.getId(), file.getSize());
        d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        repository.refreshState(d);
        requireEdit(d, user);
        requireReportMutable(d);

        requireReportMutable(d);
        localFileStorage.store(d.getS3Key(), file);

        d.setFileSize(file.getSize());

        if (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()) {
            d.setFileName(file.getOriginalFilename());
        }

        // Keep the existing extension-based fileType
        // The HTTP MIME type is detected when the file is downloaded.
        repository.save(d);

        // Ghi Audit Log sau khi upload thành công
        logUploadAfterCommit(
                username, "UPLOAD", id, null, username + " tải lên file " + d.getFileName());
    }

    @Transactional(readOnly = true)
    public LocalDownloadResult downloadLocalFile(Long id, String username) {
        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);
        requireDownload(d, user);

        Resource resource = localFileStorage.load(d.getS3Key());

        if (resource == null) {
            throw new IllegalStateException(
                    "File này chưa được lưu vào bộ nhớ local. Hãy tải lại file từ giao diện.");
        }

        String contentType = "application/octet-stream";

        try {
            String detected = java.nio.file.Files.probeContentType(resource.getFile().toPath());

            if (detected != null) {
                contentType = detected;
            }
        } catch (Exception ignored) {
        }

        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Không thể xác định kích thước file.", ex);
        }

        auditLogService.log(
                username, "DOWNLOAD", id, null, username + " tải xuống tài liệu " + d.getTitle());

        return new LocalDownloadResult(resource, d.getFileName(), contentType, contentLength);
    }

    @Transactional
    public PreviewResult previewLocalFile(Long id, String username) {

        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);

        // Kiểm tra quyền VIEW
        requireView(d, user);

        Resource resource = localFileStorage.load(d.getS3Key());

        if (resource == null) {
            throw new IllegalStateException(
                    "File này chưa được lưu vào bộ nhớ local. Hãy tải lại file từ giao diện.");
        }

        String contentType = "application/octet-stream";

        try {
            String detected = java.nio.file.Files.probeContentType(resource.getFile().toPath());

            if (detected != null && !detected.isBlank()) {
                contentType = detected;
            }
        } catch (Exception ignored) {
            // Giữ application/octet-stream nếu không xác định được MIME type
        }

        // Ghi Audit Log VIEW sau khi kiểm tra quyền và tìm thấy file thành công
        auditLogService.log(
                username, "VIEW", id, null, username + " xem trước tài liệu " + d.getTitle());

        return new PreviewResult(resource, d.getFileName(), contentType);
    }

    public record PreviewResult(Resource resource, String fileName, String contentType) {}

    @Transactional
    public PresignedDownloadResponse createPreviewUrl(Long id, String username) {
        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);

        // Chỉ cho phép người có quyền VIEW xem trước tài liệu
        requireView(d, user);

        // Tạo Presigned URL để đọc file trực tiếp từ S3
        var result = storageService.createDownloadUrl(bucket, d.getS3Key());

        // Ghi nhận sự kiện xem trước
        auditLogService.log(
                username,
                "VIEW",
                id,
                null,
                username + " xem trước tài liệu " + d.getTitle() + " từ S3");

        return new PresignedDownloadResponse(
                id, result.url().toString(), result.expiresInSeconds());
    }

    public record LocalDownloadResult(
            Resource resource, String fileName, String contentType, long contentLength) {}

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PresignedUploadResponse createUploadUrl(Long id, String username) {
        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);
        requireEdit(d, user);
        requireReportMutable(d);
        Long reservedSize = d.getFileSize();
        quotas.requireCapacity(d.getOwnerId(), d.getId(), reservedSize);
        d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        repository.refreshState(d);
        requireEdit(d, user);
        if (!java.util.Objects.equals(reservedSize, d.getFileSize()))
            quotas.requireCapacity(d.getOwnerId(), d.getId(), d.getFileSize());
        var result =
                storageService.createUploadUrl(
                        bucket, d.getS3Key(), d.getFileType(), d.getFileSize());
        return new PresignedUploadResponse(
                id, d.getS3Key(), result.url().toString(), result.expiresInSeconds());
    }

    @Transactional
    public PresignedDownloadResponse createDownloadUrl(Long id, String username) {
        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);

        // Kiểm tra quyền DOWNLOAD
        requireDownload(d, user);

        // Tạo Presigned URL để tải file trực tiếp từ S3
        var result = storageService.createDownloadUrl(bucket, d.getS3Key());

        // Ghi Audit Log
        auditLogService.log(
                username,
                "DOWNLOAD",
                id,
                null,
                username + " tải xuống tài liệu " + d.getTitle() + " từ S3");

        return new PresignedDownloadResponse(
                id, result.url().toString(), result.expiresInSeconds());
    }

    private com.clouddocs.auth.User currentUser(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
    }

    private boolean isAdmin(com.clouddocs.auth.User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private void requireView(Document d, com.clouddocs.auth.User user) {
        if (!d.isAvailable() || !permissionService.canView(d, user.getUsername(), isAdmin(user)))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền xem tài liệu");
    }

    private void requireDownload(Document d, com.clouddocs.auth.User user) {
        if (!d.isAvailable() || !permissionService.canDownload(d, user.getUsername(), isAdmin(user)))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền tải tài liệu");
    }

    private void requireEdit(Document d, com.clouddocs.auth.User user) {
        if (!d.isAvailable() || !permissionService.canEdit(d, user.getUsername(), isAdmin(user)))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền chỉnh sửa tài liệu");
    }

    private void requireOwnerOrAdmin(Document d, com.clouddocs.auth.User user) {
        if (!isAdmin(user) && !permissionService.isOwner(d, user.getUsername()))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chỉ OWNER hoặc ADMIN mới được xóa tài liệu");
    }

    private void requireReportMutable(Document document) {
        if (workTasks.existsByReportDocumentIdAndStatusIn(document.getId(),List.of("SUBMITTED","COMPLETED")))
            throw new IllegalStateException("Tài liệu đang là báo cáo chờ duyệt hoặc đã hoàn thành. Không thể xóa hoặc ghi đè file này");
    }

    private Document get(Long id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q.toLowerCase());
    }

    private List<DocumentResponse> toResponses(List<Document> documents, com.clouddocs.auth.User user) {
        var names = DocumentOwnerNames.resolve(documents, users);
        return documents.stream().map(d -> toResponse(d, user, DocumentOwnerNames.name(d, names))).toList();
    }

    private DocumentResponse toResponse(Document d, com.clouddocs.auth.User user) {
        String name = java.util.Objects.equals(d.getOwnerId(), user.getId())
                ? com.clouddocs.auth.ProfilePresentation.name(user)
                : DocumentOwnerNames.name(d, DocumentOwnerNames.resolve(List.of(d), users));
        return toResponse(d, user, name);
    }

    private DocumentResponse toResponse(Document d, com.clouddocs.auth.User user, String ownerName) {
        return new DocumentResponse(
                d.getId(),
                d.getTitle(),
                d.getDescription(),
                d.getFileName(),
                d.getFileType(),
                d.getFileSize(),
                ownerName,
                d.getDepartment(),
                d.getStatus(),
                d.getUpdatedAt().toString(),
                permissionService.effectiveAccess(d, user));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void completeS3Upload(Long id, String username) {
        Document d = get(id);
        com.clouddocs.auth.User user = currentUser(username);

        requireEdit(d, user);

        requireReportMutable(d);
        var head = storageService.headObject(bucket, d.getS3Key());

        if (head == null) {
            throw new IllegalStateException("Không thể xác nhận file đã được upload lên S3.");
        }

        quotas.requireCapacity(d.getOwnerId(), d.getId(), head.contentLength());
        d = repository.findForUpdate(id).orElseThrow(() -> new DocumentNotFoundException(id));
        repository.refreshState(d);
        requireEdit(d, user);
        d.setFileSize(head.contentLength());

        repository.save(d);

        logUploadAfterCommit(
                username,
                "UPLOAD",
                id,
                null,
                username + " tải file " + d.getFileName() + " lên S3");
    }

    // Audit uses REQUIRES_NEW. Wait until the owner quota lock and new document
    // are committed so the audit transaction does not block on their foreign keys.
    private void logUploadAfterCommit(
            String username, String action, Long documentId, Long targetUserId, String details) {
        Runnable write =
                () -> auditLogService.log(username, action, documentId, targetUserId, details);
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            write.run();
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    write.run();
                                } catch (RuntimeException ex) {
                                    org.slf4j.LoggerFactory.getLogger(DocumentService.class)
                                            .error(
                                                    "Không ghi được audit {} cho tài liệu {} đã commit",
                                                    action,
                                                    documentId,
                                                    ex);
                                }
                            }
                        });
    }
}
