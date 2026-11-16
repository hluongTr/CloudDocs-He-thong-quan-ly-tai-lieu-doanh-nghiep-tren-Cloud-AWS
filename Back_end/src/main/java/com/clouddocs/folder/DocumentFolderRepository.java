package com.clouddocs.folder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, Long> {
    List<DocumentFolder> findByOwnerIdOrderByNameAsc(Long ownerId);
}
