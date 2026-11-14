package com.clouddocs.work;
import com.clouddocs.work.WorkTaskDtos.*;
import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import com.clouddocs.permission.*;
import com.clouddocs.notification.NotificationService;
import com.clouddocs.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@Service @Transactional
public class WorkTaskService {
    private final WorkTaskRepository tasks;
    private final UserRepository users;
    private final CognitoIdentityRepository identities;
    private final DocumentRepository documents;
    private final DocumentPermissionRepository permissions;
    private final NotificationService notifications;
    private final AuditLogService audit;
    public WorkTaskService(WorkTaskRepository tasks,UserRepository users,CognitoIdentityRepository identities,DocumentRepository documents,
            DocumentPermissionRepository permissions,NotificationService notifications,AuditLogService audit){
        this.tasks=tasks;this.users=users;this.identities=identities;this.documents=documents;this.permissions=permissions;
        this.notifications=notifications;this.audit=audit;
    }
    @Transactional(readOnly=true)
    public List<Response> list(String username){User actor=actor(username);return tasks.findVisible(actor.getId(),admin(actor)).stream().map(this::response).toList();}
    @Transactional(readOnly=true)
    public Response detail(Long id,String username){User actor=actor(username);WorkTask task=get(id,false);requireVisible(task,actor);return response(task);}
    @Transactional(readOnly=true)
    public List<Person> assignees(String username){User actor=actor(username);requireAssignor(actor);
        return users.findCognitoUsers().stream()
                .filter(u->("USER".equalsIgnoreCase(u.getRole())||admin(actor)&&"MANAGER".equalsIgnoreCase(u.getRole())) && "ACTIVE".equalsIgnoreCase(u.getStatus()) && !Objects.equals(u.getId(),actor.getId()) && (admin(actor)||DepartmentScope.same(actor,u))).map(this::person).toList();}
    @Transactional(readOnly=true)
    public List<ReportDocument> reportDocuments(String username){User actor=actor(username);
        List<Document> owned=documents.findByOwnerId(actor.getId()).stream().filter(Document::isAvailable).toList();
        if(owned.isEmpty())return List.of();
        Set<Long> confirmed=new HashSet<>(documents.findUploadConfirmedIds(owned.stream().map(Document::getId).toList()));
        return owned.stream().filter(d->confirmed.contains(d.getId())).map(this::report).toList();}
    public Response create(CreateRequest request,String username){
        User actor=actor(username);requireAssignor(actor);
        if(request.dueAt()==null || !request.dueAt().isAfter(Instant.now()))throw new IllegalArgumentException("Hạn nộp phải ở tương lai");
        User assignee=users.findById(request.assigneeId()).orElseThrow(()->new IllegalArgumentException("Không tìm thấy người nhận việc"));
        if(!"ACTIVE".equalsIgnoreCase(assignee.getStatus()) || !("USER".equalsIgnoreCase(assignee.getRole())||admin(actor)&&"MANAGER".equalsIgnoreCase(assignee.getRole())) || Objects.equals(actor.getId(),assignee.getId()) || !identities.existsByUserId(assignee.getId()))
            throw new IllegalArgumentException("Chọn một nhân viên USER đang hoạt động để giao việc");
        if(DepartmentScope.manager(actor)&&!DepartmentScope.same(actor,assignee))throw new AccessDeniedException("Chỉ được giao việc cho nhân viên trong phòng ban của mình");
        WorkTask task=new WorkTask();task.title=request.title().trim();task.description=request.description().trim();
        task.assignedBy=actor;task.assignee=assignee;task.dueAt=request.dueAt();
        task=tasks.saveAndFlush(task);
        event(task,actor,assignee,"TASK_ASSIGNED","đã giao công việc",null);
        return response(task);
    }
    public Response start(Long id,StartRequest request,String username){
        User actor=actor(username);WorkTask task=get(id,true);requireAssignee(task,actor);checkVersion(task,request.version());
        requireStatus(task,"TODO");task.status="IN_PROGRESS";task=tasks.saveAndFlush(task);
        event(task,actor,task.assignedBy,"TASK_STARTED","đã bắt đầu công việc",null);return response(task);
    }
    public Response submit(Long id,SubmitRequest request,String username){
        User actor=actor(username);WorkTask task=get(id,true);requireAssignee(task,actor);checkVersion(task,request.version());
        if(!List.of("TODO","IN_PROGRESS").contains(task.status))throw new IllegalStateException("Chỉ nộp báo cáo khi công việc chưa hoàn thành và chưa chờ duyệt");
        Document document=documents.findForUpdate(request.documentId()).orElseThrow(()->new DocumentNotFoundException(request.documentId()));
        documents.refreshState(document);
        if(!document.isAvailable() || !Objects.equals(document.getOwnerId(),actor.getId()))throw new AccessDeniedException("Báo cáo phải là tài liệu đang hoạt động do bạn sở hữu");
        if(!documents.findUploadConfirmedIds(List.of(document.getId())).contains(document.getId()))
            throw new IllegalStateException("Tài liệu chưa xác nhận tải lên thành công. Hãy tải xong file trước khi nộp");
        // Preserve stronger shares; never downgrade an existing permission.
        if(!Objects.equals(document.getOwnerId(),task.assignedBy.getId()) && permissions.findByDocumentIdAndUserId(document.getId(),task.assignedBy.getId()).isEmpty()){
            DocumentPermission share=new DocumentPermission();share.setDocument(document);share.setUser(task.assignedBy);share.setPermission(DocumentPermissionType.VIEW);permissions.save(share);
            audit.logInTransaction(username,"SHARE",document.getId(),task.assignedBy.getId(),ProfilePresentation.name(actor)+" chia sẻ báo cáo cho "+ProfilePresentation.name(task.assignedBy));
        }
        task.reportDocument=document;task.reportNote=clean(request.note());task.reviewFeedback=null;task.reviewedAt=null;
        task.submittedAt=Instant.now();task.status="SUBMITTED";task=tasks.saveAndFlush(task);
        event(task,actor,task.assignedBy,"TASK_SUBMITTED","đã nộp báo cáo cho công việc",document.getId());return response(task);
    }
    public Response review(Long id,ReviewRequest request,boolean approve,String username){
        User actor=actor(username);requireAssignor(actor);WorkTask task=get(id,true);
        if(!Objects.equals(task.assignedBy.getId(),actor.getId()))throw new AccessDeniedException("Chỉ người giao việc được duyệt hoặc yêu cầu sửa báo cáo");
        checkVersion(task,request.version());requireStatus(task,"SUBMITTED");
        String feedback=clean(request.feedback());
        if(!approve && (feedback==null || feedback.isBlank()))throw new IllegalArgumentException("Nhập lý do yêu cầu chỉnh sửa");
        if(approve && (task.reportDocument==null || !task.reportDocument.isAvailable()))throw new IllegalStateException("Tài liệu báo cáo không còn khả dụng");
        task.reviewFeedback=feedback;task.reviewedAt=Instant.now();task.status=approve?"COMPLETED":"IN_PROGRESS";
        task=tasks.saveAndFlush(task);
        event(task,actor,task.assignee,approve?"TASK_APPROVED":"TASK_CHANGES_REQUESTED",approve?"đã duyệt hoàn thành công việc":"đã yêu cầu sửa báo cáo công việc",task.reportDocument==null?null:task.reportDocument.getId());
        return response(task);
    }
    public void delete(Long id,Long version,String username){
        User actor=actor(username);requireAssignor(actor);WorkTask task=get(id,true);
        if(!Objects.equals(task.assignedBy.getId(),actor.getId()))throw new AccessDeniedException("Chỉ người giao việc được xóa công việc");
        checkVersion(task,version);
        audit.logInTransaction(username,"TASK_DELETED",task.reportDocument==null?null:task.reportDocument.getId(),task.assignee.getId(),
                ProfilePresentation.name(actor)+" đã xóa công việc: "+task.title+" (#"+task.id+")");
        notifications.removeForTask(id);
        tasks.delete(task);
    }
    private WorkTask get(Long id,boolean lock){return (lock?tasks.findForUpdate(id):tasks.findById(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy công việc"));}
    private User actor(String username){User user=users.findByUsernameIgnoreCase(username).orElseThrow(()->new AccessDeniedException("Không tìm thấy tài khoản"));
        if(!"ACTIVE".equalsIgnoreCase(user.getStatus()))throw new AccessDeniedException("Tài khoản không hoạt động");return user;}
    private boolean admin(User user){return "ADMIN".equalsIgnoreCase(user.getRole());}
    private void requireAssignor(User user){if(admin(user)||DepartmentScope.manager(user)&&DepartmentScope.assigned(user))return;throw new AccessDeniedException("Chỉ admin hoặc trưởng phòng đã được gán phòng ban mới được giao việc và duyệt báo cáo");}
    private void requireVisible(WorkTask task,User user){if(!admin(user)&&!Objects.equals(task.assignee.getId(),user.getId())&&!Objects.equals(task.assignedBy.getId(),user.getId()))throw new AccessDeniedException("Bạn không có quyền xem công việc này");}
    private void requireAssignee(WorkTask task,User user){if(!Objects.equals(task.assignee.getId(),user.getId()))throw new AccessDeniedException("Chỉ người được giao việc được cập nhật và nộp báo cáo");}
    private void checkVersion(WorkTask task,Long version){if(version==null || !Objects.equals(version,task.version))throw new IllegalStateException("Công việc đã thay đổi. Hãy mở lại trước khi thực hiện thao tác");}
    private void requireStatus(WorkTask task,String status){if(!status.equals(task.status))throw new IllegalStateException("Trạng thái công việc không cho phép thao tác này");}
    private String clean(String value){return value==null?null:value.trim();}
    private Person person(User user){return new Person(user.getId(),ProfilePresentation.name(user),user.getDepartment());}
    private ReportDocument report(Document d){return new ReportDocument(d.getId(),d.getTitle(),d.getFileName());}
    private Response response(WorkTask t){boolean awaiting=List.of("TODO","IN_PROGRESS").contains(t.status);
        return new Response(t.id,t.title,t.description,person(t.assignedBy),person(t.assignee),t.dueAt,t.status,
                awaiting&&t.dueAt.isBefore(Instant.now()),t.submittedAt!=null&&t.submittedAt.isAfter(t.dueAt),
                t.reportDocument==null?null:report(t.reportDocument),t.reportDocument!=null&&t.reportDocument.isAvailable(),
                t.reportNote,t.reviewFeedback,t.submittedAt,t.reviewedAt,t.createdAt,t.updatedAt,t.version);}
    private void event(WorkTask task,User actor,User target,String action,String verb,Long documentId){
        String details=ProfilePresentation.name(actor)+" "+verb+": "+task.title+" (#"+task.id+")";
        audit.logInTransaction(actor.getUsername(),action,documentId,target.getId(),details);
        notifications.sendTaskNotification(actor,target,task.id,"Công việc: "+task.title,details+". Hạn nộp: "+java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(task.dueAt));
    }
}
