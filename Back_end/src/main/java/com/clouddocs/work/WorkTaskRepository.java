package com.clouddocs.work;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface WorkTaskRepository extends JpaRepository<WorkTask,Long> {
    @EntityGraph(attributePaths={"assignedBy","assignee","reportDocument"})
    @Query("select t from WorkTask t where :admin=true or t.assignee.id=:userId or t.assignedBy.id=:userId order by t.createdAt desc,t.id desc")
    List<WorkTask> findVisible(@Param("userId") Long userId,@Param("admin") boolean admin);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WorkTask t where t.id=:id")
    Optional<WorkTask> findForUpdate(@Param("id") Long id);
    boolean existsByReportDocumentIdAndStatusIn(Long documentId,Collection<String> statuses);
    @Query("select case when count(t)>0 then true else false end from WorkTask t where (t.assignee.id=:userId or t.assignedBy.id=:userId) and t.status<>'COMPLETED'")
    boolean hasOpenTasks(@Param("userId") Long userId);
}
