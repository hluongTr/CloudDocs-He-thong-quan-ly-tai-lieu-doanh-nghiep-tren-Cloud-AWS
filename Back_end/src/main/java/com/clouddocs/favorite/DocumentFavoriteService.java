package com.clouddocs.favorite;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.Document;
import com.clouddocs.document.DocumentRepository;
import com.clouddocs.permission.DocumentPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DocumentFavoriteService {
    private final DocumentFavoriteRepository favorites;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final DocumentPermissionService permissions;

    public DocumentFavoriteService(
            DocumentFavoriteRepository favorites,
            DocumentRepository documents,
            UserRepository users,
            DocumentPermissionService permissions) {
        this.favorites = favorites;
        this.documents = documents;
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<Long> list(String username) {
        User user = user(username);
        boolean admin = "ADMIN".equalsIgnoreCase(user.getRole());
        return favorites.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(DocumentFavorite::getDocument)
                .filter(document -> permissions.canView(document, username, admin))
                .map(Document::getId)
                .toList();
    }

    @Transactional
    public void add(Long documentId, String username) {
        User user = user(username);
        Document document = document(documentId);
        boolean admin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (!permissions.canView(document, username, admin)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền xem tài liệu này");
        }
        if (favorites.findByUserIdAndDocumentId(user.getId(), documentId).isEmpty()) {
            DocumentFavorite favorite = new DocumentFavorite();
            favorite.setUser(user);
            favorite.setDocument(document);
            favorites.save(favorite);
        }
    }

    @Transactional
    public void remove(Long documentId, String username) {
        User user = user(username);
        favorites.findByUserIdAndDocumentId(user.getId(), documentId).ifPresent(favorites::delete);
    }

    private User user(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
    }

    private Document document(Long id) {
        return documents
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu"));
    }
}
