package com.clouddocs.folder;

import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import com.clouddocs.audit.AuditLogService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class FolderService {
    private final DocumentFolderRepository folders;
    private final UserRepository users;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final AuditLogService audit;

    public FolderService(DocumentFolderRepository folders, UserRepository users,
            DocumentRepository documents, DocumentService documentService, AuditLogService audit) {
        this.folders = folders; this.users = users; this.documents = documents;
        this.documentService = documentService; this.audit = audit;
    }
    private User user(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
    }
    // Serialize folder writes per owner, including assignment and recursive deletion.
    private User lockUser(String username) {
        User u = user(username);
        return users.findForStorageUpdate(u.getId()).orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
    }
    private DocumentFolder owned(Long id, Long owner) {
        DocumentFolder f = folders.findById(id).orElseThrow(() -> new IllegalArgumentException("Thư mục không còn tồn tại"));
        if (!Objects.equals(f.getOwnerId(), owner)) throw new AccessDeniedException("Không có quyền quản lý thư mục này");
        return f;
    }
    private User lockFolderOwner(Long id, String username) {
        User actor = user(username);
        DocumentFolder f = folders.findById(id).orElseThrow(() -> new IllegalArgumentException("Thư mục không còn tồn tại"));
        if (!Objects.equals(f.getOwnerId(), actor.getId()) && !"ADMIN".equalsIgnoreCase(actor.getRole()))
            throw new AccessDeniedException("Không có quyền quản lý thư mục này");
        return users.findForStorageUpdate(f.getOwnerId()).orElseThrow(() -> new IllegalArgumentException("Chủ sở hữu không tồn tại"));
    }
    private String name(String value, Long parent, Long owner, Long except) {
        String n = value == null ? "" : value.trim();
        if (n.isEmpty() || n.length() > 120 || n.contains("/") || n.contains("\\") || n.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Tên thư mục phải có 1–120 ký tự, không chứa dấu / hoặc \\");
        boolean duplicate = folders.findByOwnerIdOrderByNameAsc(owner).stream().anyMatch(f ->
                Objects.equals(f.getParentId(), parent) && !Objects.equals(f.getId(), except) && f.getName().equalsIgnoreCase(n));
        if (duplicate) throw new IllegalArgumentException("Đã có thư mục cùng tên tại vị trí này");
        return n;
    }
    @Transactional(readOnly = true)
    public List<FolderView> list(String username) {
        User viewer = user(username);
        List<DocumentFolder> visible = "ADMIN".equalsIgnoreCase(viewer.getRole())
                ? folders.findAll() : folders.findByOwnerIdOrderByNameAsc(viewer.getId());
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> departments = new HashMap<>();
        users.findAllById(visible.stream().map(DocumentFolder::getOwnerId).distinct().toList())
                .forEach(owner -> { names.put(owner.getId(), ProfilePresentation.name(owner)); departments.put(owner.getId(), owner.getDepartment()); });
        return visible.stream().sorted(Comparator.comparing(DocumentFolder::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DocumentFolder::getId)).map(f -> new FolderView(f.getId(), f.getOwnerId(),
                f.getParentId(), f.getName(), names.getOrDefault(f.getOwnerId(), "Tài khoản #" + f.getOwnerId()),
                Objects.equals(f.getOwnerId(), viewer.getId()) || "ADMIN".equalsIgnoreCase(viewer.getRole()),
                departments.getOrDefault(f.getOwnerId(), ""), Objects.equals(f.getOwnerId(), viewer.getId()))).toList();
    }
    public record FolderView(Long id, Long ownerId, Long parentId, String name, String ownerName, boolean canManage, String ownerDepartment, boolean canOrganize) {}

    @Transactional
    public DocumentFolder create(String value, Long parent, String username) {
        User u = lockUser(username);
        if (parent != null) owned(parent, u.getId());
        DocumentFolder f = new DocumentFolder(); f.setOwnerId(u.getId()); f.setParentId(parent);
        f.setName(name(value, parent, u.getId(), null));
        folders.save(f);
        audit.logInTransaction(username, "CREATE_FOLDER", null, null, "Tạo thư mục: " + f.getName());
        return f;
    }
    @Transactional
    public DocumentFolder rename(Long id, String value, String username) {
        User u = lockFolderOwner(id, username); DocumentFolder f = owned(id, u.getId());
        f.setName(name(value, f.getParentId(), u.getId(), id));
        audit.logInTransaction(username, "RENAME_FOLDER", null, null, "Đổi tên thư mục thành: " + f.getName());
        return folders.save(f);
    }
    @Transactional
    public void move(List<Long> ids, Long destination, String username) {
        User u = lockUser(username);
        if (destination != null) owned(destination, u.getId());
        if (ids == null || ids.isEmpty() || ids.size() > 500 || ids.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Chọn từ 1 đến 500 tài liệu");
        for (Long id : new TreeSet<>(ids)) {
            Document d = documents.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại"));
            if (!Objects.equals(d.getOwnerId(), u.getId())) throw new AccessDeniedException("Chỉ được di chuyển tài liệu của mình");
            if (!d.isAvailable()) throw new IllegalArgumentException("Tài liệu không còn trong kho");
            d.setFolderId(destination); documents.save(d);
            audit.logInTransaction(username, "MOVE_DOCUMENT", id, null, "Di chuyển tài liệu: " + d.getTitle());
        }
    }
    @Transactional
    public void delete(Long id, String username) {
        User u = lockFolderOwner(id, username); DocumentFolder root = owned(id, u.getId());
        List<DocumentFolder> all = folders.findByOwnerIdOrderByNameAsc(u.getId());
        Set<Long> subtree = new HashSet<>(); subtree.add(id);
        boolean changed;
        do { changed = false; for (DocumentFolder f : all) if (subtree.contains(f.getParentId()) && subtree.add(f.getId())) changed = true; } while (changed);
        List<Document> affected = documents.findByOwnerId(u.getId()).stream()
                .filter(d -> subtree.contains(d.getFolderId())).sorted(Comparator.comparing(Document::getId)).toList();
        for (Document d : affected) {
            // Reuse report protections and audit events. Any failure rolls back the entire subtree.
            if (d.isAvailable()) documentService.delete(d.getId(), username);
            d.setFolderId(null); documents.save(d);
        }
        // Clear parent links before removal; no S3 objects are moved or deleted.
        for (DocumentFolder f : all) if (subtree.contains(f.getId())) f.setParentId(null);
        folders.flush();
        folders.deleteAll(all.stream().filter(f -> subtree.contains(f.getId())).toList());
        audit.logInTransaction(username, "DELETE_FOLDER", null, null, "Xóa thư mục và chuyển tài liệu vào thùng rác: " + root.getName());
    }
}
