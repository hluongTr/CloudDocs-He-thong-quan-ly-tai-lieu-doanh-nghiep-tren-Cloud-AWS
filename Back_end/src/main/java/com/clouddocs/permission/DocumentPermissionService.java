package com.clouddocs.permission;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.Document;
import com.clouddocs.document.DocumentRepository;
import com.clouddocs.permission.DocumentPermissionDtos.PermissionRequest;
import com.clouddocs.permission.DocumentPermissionDtos.PermissionResponse;
import com.clouddocs.permission.DocumentPermissionDtos.ShareableUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.clouddocs.audit.AuditLogService;

import java.util.List;

@Service
public class DocumentPermissionService {

    private final DocumentPermissionRepository permissions;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final AuditLogService auditLogService;

    public DocumentPermissionService(
            DocumentPermissionRepository permissions,
            DocumentRepository documents,
            UserRepository users,
            AuditLogService auditLogService) {
        this.permissions = permissions;
        this.documents = documents;
        this.users = users;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> list(Long documentId, String username) {
        Document document = getDocument(documentId);
        requireOwnerOrAdmin(document, username);
        return permissions.findAllByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShareableUserResponse> listShareableUsers(Long documentId, String username) {
        Document document = getDocument(documentId);
        requireOwnerOrAdmin(document, username);

        Long ownerId = document.getOwnerId();
        if (ownerId == null) {
            return users.findAllByStatusIgnoreCaseAndIdNotOrderByFullNameAsc("ACTIVE", -1L).stream()
                    .map(this::toShareableUser)
                    .toList();
        }
        return users.findAllByStatusIgnoreCaseAndIdNotOrderByFullNameAsc("ACTIVE", ownerId).stream()
                .map(this::toShareableUser)
                .toList();
    }

    @Transactional
    public PermissionResponse grantOrUpdate(
            Long documentId, Long userId, PermissionRequest request, String username) {

        Document document = getDocument(documentId);
        requireOwnerOrAdmin(document, username);

        User target =
                users.findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Không tìm thấy người dùng được chia sẻ"));

        if (document.getOwnerId() != null && document.getOwnerId().equals(target.getId())) {
            throw new IllegalArgumentException(
                    "Chủ sở hữu tài liệu không cần cấp quyền cho chính mình");
        }

        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw new IllegalStateException("Không thể cấp quyền cho tài khoản đang bị khóa");
        }

        DocumentPermission permission =
                permissions
                        .findByDocumentIdAndUserId(documentId, userId)
                        .orElseGet(DocumentPermission::new);

        permission.setDocument(document);
        permission.setUser(target);
        permission.setPermission(request.permission());

        DocumentPermission savedPermission = permissions.save(permission);

        // Ghi Audit Log sau khi cấp/cập nhật quyền thành công
        auditLogService.logInTransaction(
                username,
                "SHARE",
                documentId,
                target.getId(),
                username
                        + " chia sẻ tài liệu cho "
                        + target.getUsername()
                        + " với quyền "
                        + request.permission().name());

        return toResponse(savedPermission);
    }

    @Transactional
    public void deleteAllForDocument(Long documentId) {
        permissions.deleteAllByDocumentId(documentId);
    }

    @Transactional
    public void revoke(Long documentId, Long userId, String username) {

        Document document = getDocument(documentId);
        requireOwnerOrAdmin(document, username);

        permissions
                .findByDocumentIdAndUserId(documentId, userId)
                .ifPresent(
                        permission -> {
                            User target = permission.getUser();

                            permissions.delete(permission);

                            // Ghi Audit Log sau khi thu hồi quyền thành công
                            auditLogService.logInTransaction(
                                    username,
                                    "REVOKE_PERMISSION",
                                    documentId,
                                    target.getId(),
                                    username + " thu hồi quyền của " + target.getUsername());
                        });
    }

    @Transactional(readOnly = true)
    public boolean canView(Document document, String username, boolean admin) {
        if (!document.isAvailable()) return false;
        if (admin) return true;
        if (isOwner(document, username)) return true;
        return has(
                document,
                username,
                List.of(
                        DocumentPermissionType.VIEW,
                        DocumentPermissionType.DOWNLOAD,
                        DocumentPermissionType.EDIT));
    }

    @Transactional(readOnly = true)
    public boolean canDownload(Document document, String username, boolean admin) {
        if (!document.isAvailable()) return false;
        if (admin) return true;
        if (isOwner(document, username)) return true;
        return has(
                document,
                username,
                List.of(DocumentPermissionType.DOWNLOAD, DocumentPermissionType.EDIT));
    }

    @Transactional(readOnly = true)
    public boolean canEdit(Document document, String username, boolean admin) {
        if (!document.isAvailable()) return false;
        if (admin) return true;
        if (isOwner(document, username)) return true;
        return has(document, username, List.of(DocumentPermissionType.EDIT));
    }

    @Transactional(readOnly = true)
    public String effectiveAccess(Document document, User user) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) return "ADMIN";
        if (isOwner(document, user.getUsername())) return "OWNER";
        if (has(document, user.getUsername(), List.of(DocumentPermissionType.EDIT))) return "EDIT";
        if (has(document, user.getUsername(), List.of(DocumentPermissionType.DOWNLOAD)))
            return "DOWNLOAD";
        if (has(document, user.getUsername(), List.of(DocumentPermissionType.VIEW))) return "VIEW";
        return "NONE";
    }

    public boolean isOwner(Document document, String username) {
        return document.getOwnerId() != null && users.findByUsernameIgnoreCase(username)
                .map(user -> document.getOwnerId().equals(user.getId())).orElse(false);
    }

    private boolean has(Document document, String username, List<DocumentPermissionType> types) {
        User user =
                users.findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        return permissions.hasAnyPermission(document.getId(), user.getId(), types);
    }

    private void requireOwnerOrAdmin(Document document, String username) {
        if (!document.isAvailable()) throw new org.springframework.security.access.AccessDeniedException("Tài liệu đang ở thùng rác hoặc đã xóa vĩnh viễn");
        User actor =
                users.findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        if (!"ADMIN".equalsIgnoreCase(actor.getRole()) && !isOwner(document, username)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chỉ OWNER hoặc ADMIN mới được quản lý quyền tài liệu");
        }
    }

    private Document getDocument(Long id) {
        return documents
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu"));
    }

    private ShareableUserResponse toShareableUser(User u) {
        return new ShareableUserResponse(
                u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getDepartment());
    }

    private PermissionResponse toResponse(DocumentPermission p) {
        User u = p.getUser();
        return new PermissionResponse(
                p.getId(),
                p.getDocument().getId(),
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                p.getPermission().name(),
                p.getCreatedAt());
    }
}
