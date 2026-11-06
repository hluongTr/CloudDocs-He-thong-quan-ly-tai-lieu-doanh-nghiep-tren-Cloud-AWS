package com.clouddocs.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository
        extends JpaRepository<Document, Long>, DocumentStateRepository {
    @Query("select distinct a.document.id from AuditLog a where a.action = 'UPLOAD' and a.document.id in :ids")
    List<Long> findUploadConfirmedIds(@Param("ids") java.util.Collection<Long> ids);

    @Query(
            """
        select distinct d from Document d
        where d.ownerId = :userId
           or exists (select dp.id from DocumentPermission dp
                      where dp.document.id = d.id and dp.user.id = :userId)
        order by d.updatedAt desc
        """)
    List<Document> findAccessibleByUserId(@Param("userId") Long userId);

    @Query(
            """
        select d from Document d
        where exists (select dp.id from DocumentPermission dp
                      where dp.document.id = d.id and dp.user.id = :userId)
        order by d.updatedAt desc
        """)
    List<Document> findSharedByUserId(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    java.util.Optional<Document> findForUpdate(@Param("id") Long id);

    boolean existsByOwnerIdAndStatusNot(Long ownerId, String status);

    @Query("select coalesce(sum(d.fileSize), 0) from Document d where d.ownerId = :ownerId")
    long sumStorageByOwnerId(@Param("ownerId") Long ownerId);

    @Query(
            "select coalesce(sum(d.fileSize), 0) from Document d where d.ownerId = :ownerId and d.id <> :documentId")
    long sumStorageExcludingDocument(
            @Param("ownerId") Long ownerId, @Param("documentId") Long documentId);

    List<Document> findByOwnerId(Long ownerId);

    @Query(
            "select distinct d.department from Document d where d.department is not null and trim(d.department) <> ''")
    List<String> findDistinctDepartments();
}
