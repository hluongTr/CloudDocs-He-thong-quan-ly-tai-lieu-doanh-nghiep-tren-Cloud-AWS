package com.clouddocs.work;
import com.clouddocs.work.WorkTaskDtos.*;
import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import com.clouddocs.permission.*;
import com.clouddocs.notification.*;
import com.clouddocs.audit.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkTaskServiceTest {
    final WorkTaskRepository tasks=mock(WorkTaskRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final CognitoIdentityRepository identities=mock(CognitoIdentityRepository.class);
    final DocumentRepository documents=mock(DocumentRepository.class);
    final DocumentPermissionRepository shares=mock(DocumentPermissionRepository.class);
    final NotificationService notifications=mock(NotificationService.class);
    final AuditLogService audit=mock(AuditLogService.class);
    final WorkTaskService service=new WorkTaskService(tasks,users,identities,documents,shares,notifications,audit);
    User manager,worker,other;
    WorkTask task;
    Document report;
    User user(long id,String username,String name,String role){User u=new User();ReflectionTestUtils.setField(u,"id",id);u.setUsername(username);u.setFullName(name);u.setRole(role);u.setStatus("ACTIVE");u.setDepartment("IT");when(users.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(u));when(users.findById(id)).thenReturn(Optional.of(u));return u;}
    @BeforeEach void setup(){
        manager=user(1,"manager","Người giao việc","ADMIN");worker=user(2,"worker","Trần Hải Lương","USER");other=user(3,"other","Người khác","USER");
        task=new WorkTask();task.id=8L;task.title="Báo cáo tháng";task.description="Nộp file PDF";task.assignedBy=manager;task.assignee=worker;task.dueAt=Instant.now().plusSeconds(3600);task.version=0L;task.createdAt=Instant.now();task.updatedAt=task.createdAt;
        when(tasks.findById(8L)).thenReturn(Optional.of(task));when(tasks.findForUpdate(8L)).thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(any())).thenAnswer(call->{WorkTask t=call.getArgument(0);if(t.id==null)t.id=8L;t.version=t.version==null?0:t.version+1;return t;});
        when(identities.existsByUserId(2L)).thenReturn(true);
        report=new Document();ReflectionTestUtils.setField(report,"id",7L);report.setTitle("Báo cáo.pdf");report.setFileName("Báo cáo.pdf");report.setOwnerId(2L);report.setOwner("worker");
        when(documents.findForUpdate(7L)).thenReturn(Optional.of(report));when(documents.findUploadConfirmedIds(List.of(7L))).thenReturn(List.of(7L));
    }
    @Test void managerCreatesTaskWithOneAssigneeAndPrivateNotification(){
        Response result=service.create(new CreateRequest(" Báo cáo "," Yêu cầu ",2L,Instant.now().plusSeconds(500)),"manager");
        assertEquals("TODO",result.status());assertEquals("Trần Hải Lương",result.assignee().fullName());assertEquals("Báo cáo",result.title());
        verify(notifications).sendTaskNotification(eq(manager),eq(worker),eq(8L),anyString(),anyString());
        verify(audit).logInTransaction(eq("manager"),eq("TASK_ASSIGNED"),isNull(),eq(2L),anyString());
    }
    @Test void userCannotAssignTask(){assertThrows(AccessDeniedException.class,()->service.create(new CreateRequest("A","B",3L,Instant.now().plusSeconds(500)),"worker"));verifyNoInteractions(tasks,notifications,audit);}
    @Test void rejectsPastDeadlineLockedUserAdminAssigneeAndUnlinkedLegacyAccount(){
        assertThrows(IllegalArgumentException.class,()->service.create(new CreateRequest("A","B",2L,Instant.now().minusSeconds(1)),"manager"));
        worker.setStatus("LOCKED");assertThrows(IllegalArgumentException.class,()->service.create(new CreateRequest("A","B",2L,Instant.now().plusSeconds(500)),"manager"));
        worker.setStatus("ACTIVE");worker.setRole("ADMIN");assertThrows(IllegalArgumentException.class,()->service.create(new CreateRequest("A","B",2L,Instant.now().plusSeconds(500)),"manager"));
        worker.setRole("USER");when(identities.existsByUserId(2L)).thenReturn(false);assertThrows(IllegalArgumentException.class,()->service.create(new CreateRequest("A","B",2L,Instant.now().plusSeconds(500)),"manager"));verifyNoInteractions(notifications,audit);
    }
    @Test void unrelatedUserCannotReadOrChangeTask(){
        assertThrows(AccessDeniedException.class,()->service.detail(8L,"other"));
        assertThrows(AccessDeniedException.class,()->service.start(8L,new StartRequest(0L),"other"));
        assertThrows(AccessDeniedException.class,()->service.submit(8L,new SubmitRequest(0L,7L,""),"other"));verifyNoInteractions(notifications,audit);
    }
    @Test void adminCanReadSystemTasksButCannotApproveOthersAssignments(){
        other.setRole("ADMIN");assertEquals(8L,service.detail(8L,"other").id());task.status="SUBMITTED";
        assertThrows(AccessDeniedException.class,()->service.review(8L,new ReviewRequest(0L,"OK"),true,"other"));
    }
    @Test void startIsRestrictedToTodoAndAssignee(){
        assertEquals("IN_PROGRESS",service.start(8L,new StartRequest(0L),"worker").status());
        assertThrows(IllegalStateException.class,()->service.start(8L,new StartRequest(1L),"worker"));
        assertThrows(AccessDeniedException.class,()->service.start(8L,new StartRequest(1L),"manager"));
    }
    @Test void staleVersionCannotSubmitOrApproveNewReport(){
        task.version=2L;assertThrows(IllegalStateException.class,()->service.submit(8L,new SubmitRequest(1L,7L,""),"worker"));
        task.status="SUBMITTED";assertThrows(IllegalStateException.class,()->service.review(8L,new ReviewRequest(1L,""),true,"manager"));verifyNoInteractions(notifications,audit,shares);
    }
    @Test void submittingOwnConfirmedDocumentGrantsViewWithoutExternalMessaging(){
        Response result=service.submit(8L,new SubmitRequest(0L,7L," Kết quả "),"worker");
        assertEquals("SUBMITTED",result.status());assertEquals("Kết quả",result.reportNote());assertEquals(7L,result.reportDocument().id());
        var captor=org.mockito.ArgumentCaptor.forClass(DocumentPermission.class);verify(shares).save(captor.capture());
        assertEquals(DocumentPermissionType.VIEW,captor.getValue().getPermission());assertEquals(manager,captor.getValue().getUser());
        verify(notifications).sendTaskNotification(eq(worker),eq(manager),eq(8L),anyString(),anyString());
        verify(audit).logInTransaction(eq("worker"),eq("TASK_SUBMITTED"),eq(7L),eq(1L),anyString());
    }
    @Test void existingStrongerShareIsNeverDowngraded(){
        DocumentPermission existing=new DocumentPermission();existing.setPermission(DocumentPermissionType.EDIT);
        when(shares.findByDocumentIdAndUserId(7L,1L)).thenReturn(Optional.of(existing));
        service.submit(8L,new SubmitRequest(0L,7L,""),"worker");verify(shares,never()).save(any());assertEquals(DocumentPermissionType.EDIT,existing.getPermission());
    }
    @Test void cannotSubmitAnotherOwnersTrashedOrUnconfirmedFile(){
        report.setOwnerId(3L);assertThrows(AccessDeniedException.class,()->service.submit(8L,new SubmitRequest(0L,7L,""),"worker"));
        report.setOwnerId(2L);report.setStatus("TRASHED");assertThrows(AccessDeniedException.class,()->service.submit(8L,new SubmitRequest(0L,7L,""),"worker"));
        report.setStatus("ACTIVE");when(documents.findUploadConfirmedIds(List.of(7L))).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.submit(8L,new SubmitRequest(0L,7L,""),"worker"));verifyNoInteractions(shares,notifications,audit);
    }
    @Test void requestChangesRequiresReasonAndAllowsResubmissionThenApproval(){
        service.submit(8L,new SubmitRequest(0L,7L,"V1"),"worker");
        assertThrows(IllegalArgumentException.class,()->service.review(8L,new ReviewRequest(1L," "),false,"manager"));
        assertEquals("IN_PROGRESS",service.review(8L,new ReviewRequest(1L,"Bổ sung số liệu"),false,"manager").status());
        assertEquals("SUBMITTED",service.submit(8L,new SubmitRequest(2L,7L,"V2"),"worker").status());assertNull(task.reviewFeedback);
        assertEquals("COMPLETED",service.review(8L,new ReviewRequest(3L,"Đạt yêu cầu"),true,"manager").status());
        assertThrows(IllegalStateException.class,()->service.submit(8L,new SubmitRequest(4L,7L,"V3"),"worker"));
    }
    @Test void cannotApproveWithoutValidReport(){task.status="SUBMITTED";assertThrows(IllegalStateException.class,()->service.review(8L,new ReviewRequest(0L,""),true,"manager"));task.reportDocument=report;report.setStatus("PURGED");assertThrows(IllegalStateException.class,()->service.review(8L,new ReviewRequest(0L,""),true,"manager"));}
    @Test void deadlineLabelsDistinguishPendingLateAndCompleted(){
        task.dueAt=Instant.now().minusSeconds(60);assertTrue(service.detail(8L,"worker").overdue());
        task.status="SUBMITTED";task.submittedAt=task.dueAt.plusSeconds(1);Response submitted=service.detail(8L,"worker");assertFalse(submitted.overdue());assertTrue(submitted.lateSubmission());
        task.status="COMPLETED";assertFalse(service.detail(8L,"worker").overdue());
    }
    @Test void listingIsScopedAtRepositoryBeforeReturningData(){when(tasks.findVisible(2L,false)).thenReturn(List.of(task));assertEquals(1,service.list("worker").size());verify(tasks).findVisible(2L,false);verify(tasks,never()).findAll();}
    @Test void lockedOrDeletedAccountCannotUseTaskApi(){for(String status:List.of("LOCKED","DELETED")){worker.setStatus(status);assertThrows(AccessDeniedException.class,()->service.list("worker"));}verifyNoInteractions(tasks);}
    @Test void reportPickerContainsOnlyOwnedAvailableConfirmedDocuments(){Document pending=new Document();ReflectionTestUtils.setField(pending,"id",9L);pending.setOwnerId(2L);pending.setStatus("ACTIVE");Document trashed=new Document();ReflectionTestUtils.setField(trashed,"id",10L);trashed.setStatus("TRASHED");when(documents.findByOwnerId(2L)).thenReturn(List.of(report,pending,trashed));when(documents.findUploadConfirmedIds(List.of(7L,9L))).thenReturn(List.of(7L));assertEquals(List.of(new ReportDocument(7L,"Báo cáo.pdf","Báo cáo.pdf")),service.reportDocuments("worker"));}

    @Test void deletingTaskRequiresCreatorAdminAndCurrentVersion(){
        assertThrows(AccessDeniedException.class,()->service.delete(8L,0L,"worker"));
        other.setRole("ADMIN");assertThrows(AccessDeniedException.class,()->service.delete(8L,0L,"other"));
        assertThrows(IllegalStateException.class,()->service.delete(8L,9L,"manager"));
        verify(tasks,never()).delete(any());verifyNoInteractions(notifications,audit);
    }
    @Test void deletionKeepsReportAndWritesAuditAndRemovesTaskNotifications(){
        task.status="COMPLETED";task.reportDocument=report;
        service.delete(8L,0L,"manager");
        verify(tasks).delete(task);verify(notifications).removeForTask(8L);
        verify(audit).logInTransaction(eq("manager"),eq("TASK_DELETED"),eq(7L),eq(2L),contains(task.title));
        verifyNoInteractions(documents,shares);
    }
    @Test void overdueTaskCanStillSubmitAndIsMarkedLate(){task.dueAt=Instant.now().minusSeconds(60);
        Response result=service.submit(8L,new SubmitRequest(0L,7L,"Late report"),"worker");
        assertEquals("SUBMITTED",result.status());assertTrue(result.lateSubmission());assertFalse(result.overdue());
    }

 @Test void departmentManagerAssignsAndReviewsOnlyAsCreator(){manager.setRole("MANAGER");assertEquals("TODO",service.create(new CreateRequest("Task","Report",2L,Instant.now().plusSeconds(500)),"manager").status());task.status="SUBMITTED";task.reportDocument=report;assertEquals("COMPLETED",service.review(8L,new ReviewRequest(task.version,"OK"),true,"manager").status());}
 @Test void managerCannotAssignAcrossDepartmentsOrWithoutDepartment(){manager.setRole("MANAGER");worker.setDepartment("HR");assertThrows(AccessDeniedException.class,()->service.create(new CreateRequest("Task","Report",2L,Instant.now().plusSeconds(500)),"manager"));manager.setDepartment("Chưa cập nhật");assertThrows(AccessDeniedException.class,()->service.assignees("manager"));}
 @Test void managerAssigneePickerOnlyContainsOwnDepartmentEmployees(){manager.setRole("MANAGER");other.setDepartment("HR");when(users.findCognitoUsers()).thenReturn(List.of(manager,worker,other));assertEquals(List.of(2L),service.assignees("manager").stream().map(Person::id).toList());}
 @Test void managerCannotApproveAnotherCreatorsTask(){other.setRole("MANAGER");task.status="SUBMITTED";assertThrows(AccessDeniedException.class,()->service.review(8L,new ReviewRequest(0L,""),true,"other"));}

}
