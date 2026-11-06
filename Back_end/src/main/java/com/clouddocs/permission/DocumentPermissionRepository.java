package com.clouddocs.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findAllByDocumentIdOrderByCreatedAtAsc(Long documentId);

    Optional<DocumentPermission> findByDocumentIdAndUserId(Long documentId, Long userId);

    void deleteAllByDocumentId(Long documentId);

    @Query(
            """
        select case when count(dp) > 0 then true else false end
        from DocumentPermission dp
        where dp.document.id = :documentId
          and dp.user.id = :userId
          and dp.permission in :permissions
        """)
    boolean hasAnyPermission(
            @Param("documentId") Long documentId,
            @Param("userId") Long userId,
            @Param("permissions") List<DocumentPermissionType> permissions);
}
