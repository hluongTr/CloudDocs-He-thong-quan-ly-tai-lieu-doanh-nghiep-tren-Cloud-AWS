package com.clouddocs.audit;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = {"user", "document", "targetUser"})
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "document", "targetUser"})
    List<AuditLog> findTop20ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "document", "targetUser"})
    List<AuditLog> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "document", "targetUser"})
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByAction(String action);

    long countByActionAndUserId(String action, Long userId);

    @EntityGraph(attributePaths = {"user", "document", "targetUser"})
    @Query(
            """
        select a from AuditLog a
        where a.document is not null and (
            (a.user.id = :userId and a.action in :actions)
            or (a.targetUser.id = :userId and a.action = 'SHARE')
        )
        order by a.createdAt desc
        """)
    List<AuditLog> findRecentDocumentActivity(
            @Param("userId") Long userId, @Param("actions") List<String> actions);
}
