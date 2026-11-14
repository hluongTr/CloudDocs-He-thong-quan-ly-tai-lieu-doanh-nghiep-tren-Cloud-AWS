package com.clouddocs.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"document","user"})
    @Query("select p from DocumentPermission p where p.document.ownerId=:actorId or (:admin=true and exists(select a.id from AuditLog a where a.action='SHARE' and a.document.id=p.document.id and a.targetUser.id=p.user.id and a.user.id=:actorId and a.id=(select max(lastShare.id) from AuditLog lastShare where lastShare.action='SHARE' and lastShare.document.id=p.document.id and lastShare.targetUser.id=p.user.id))) order by p.createdAt desc,p.id desc")
    List<DocumentPermission> findOutgoing(@Param("actorId") Long actorId,@Param("admin") boolean admin);

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
