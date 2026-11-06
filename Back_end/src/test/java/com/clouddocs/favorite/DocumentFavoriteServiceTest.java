package com.clouddocs.favorite;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.Document;
import com.clouddocs.document.DocumentRepository;
import com.clouddocs.permission.DocumentPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentFavoriteServiceTest {
    private final DocumentFavoriteRepository favorites = mock(DocumentFavoriteRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DocumentPermissionService permissions = mock(DocumentPermissionService.class);
    private final DocumentFavoriteService service =
            new DocumentFavoriteService(favorites, documents, users, permissions);
    private User user;
    private Document document;

    @BeforeEach
    void setup() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 3L);
        user.setUsername("user1");
        document = new Document();
        ReflectionTestUtils.setField(document, "id", 9L);
        when(users.findByUsernameIgnoreCase("user1")).thenReturn(Optional.of(user));
        when(documents.findById(9L)).thenReturn(Optional.of(document));
    }

    @Test
    void addsFavoriteOnlyWhenUserCanViewDocument() {
        when(permissions.canView(document, "user1", false)).thenReturn(true);
        when(favorites.findByUserIdAndDocumentId(3L, 9L)).thenReturn(Optional.empty());
        service.add(9L, "user1");
        verify(favorites).save(any(DocumentFavorite.class));
    }

    @Test
    void doesNotCreateDuplicateFavorite() {
        DocumentFavorite existing = new DocumentFavorite();
        when(permissions.canView(document, "user1", false)).thenReturn(true);
        when(favorites.findByUserIdAndDocumentId(3L, 9L)).thenReturn(Optional.of(existing));
        service.add(9L, "user1");
        verify(favorites, never()).save(any());
    }

    @Test
    void rejectsFavoriteWithoutViewPermission() {
        when(permissions.canView(document, "user1", false)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.add(9L, "user1"));
        verify(favorites, never()).save(any());
    }

    @Test
    void listsOnlyFavoritesStillVisibleToUser() {
        Document visible = document;
        Document hidden = new Document();
        ReflectionTestUtils.setField(hidden, "id", 10L);
        DocumentFavorite visibleFavorite = new DocumentFavorite();
        visibleFavorite.setDocument(visible);
        DocumentFavorite hiddenFavorite = new DocumentFavorite();
        hiddenFavorite.setDocument(hidden);
        when(favorites.findByUserIdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of(visibleFavorite, hiddenFavorite));
        when(permissions.canView(visible, "user1", false)).thenReturn(true);
        when(permissions.canView(hidden, "user1", false)).thenReturn(false);
        assertEquals(List.of(9L), service.list("user1"));
    }

    @Test
    void removesOnlyCurrentUsersFavorite() {
        DocumentFavorite favorite = new DocumentFavorite();
        when(favorites.findByUserIdAndDocumentId(3L, 9L)).thenReturn(Optional.of(favorite));
        service.remove(9L, "user1");
        verify(favorites).delete(favorite);
    }
}
