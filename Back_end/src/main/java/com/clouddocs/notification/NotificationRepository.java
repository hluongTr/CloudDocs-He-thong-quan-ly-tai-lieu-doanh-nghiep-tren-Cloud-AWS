package com.clouddocs.notification;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    void deleteByWorkTaskId(Long workTaskId);
    @EntityGraph(attributePaths = "createdBy")
    @org.springframework.data.jpa.repository.Query("select n from Notification n where n.recipientId is null or n.recipientId=:userId order by n.createdAt desc,n.id desc")
    List<Notification> findVisible(@org.springframework.data.repository.query.Param("userId") Long userId,org.springframework.data.domain.Pageable pageable);
}
