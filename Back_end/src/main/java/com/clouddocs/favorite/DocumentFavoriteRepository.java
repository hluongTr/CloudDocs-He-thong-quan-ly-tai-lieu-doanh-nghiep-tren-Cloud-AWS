package com.clouddocs.favorite;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, Long> {
    @EntityGraph(attributePaths = "document")
    List<DocumentFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<DocumentFavorite> findByUserIdAndDocumentId(Long userId, Long documentId);

    void deleteAllByDocumentId(Long documentId);
}
