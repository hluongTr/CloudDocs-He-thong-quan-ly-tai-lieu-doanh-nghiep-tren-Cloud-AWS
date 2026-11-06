package com.clouddocs.notification;

import com.clouddocs.audit.AuditLogService;
import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.notification.NotificationDtos.CreateNotificationRequest;
import com.clouddocs.notification.NotificationDtos.NotificationResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificationService {
    @org.springframework.transaction.annotation.Transactional
    public void removeForTask(Long taskId){notifications.deleteByWorkTaskId(taskId);}

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final AuditLogService auditLogs;

    public NotificationService(
            NotificationRepository notifications, UserRepository users, AuditLogService auditLogs) {
        this.notifications = notifications;
        this.users = users;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String username) {
        User actor = users.findByUsernameIgnoreCase(username).orElseThrow();
        return notifications.findVisible(actor.getId(), org.springframework.data.domain.PageRequest.of(0,50)).stream()
                .map(value -> response(value, actor))
                .toList();
    }

    @Transactional
    public NotificationResponse create(String username, CreateNotificationRequest request) {
        User admin =
                users.findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        if (!"ADMIN".equalsIgnoreCase(admin.getRole())) {
            throw new AccessDeniedException("Chỉ ADMIN được gửi thông báo chung");
        }
        Notification notification = new Notification();
        notification.setTitle(request.title().trim());
        notification.setMessage(request.message().trim());
        notification.setCreatedBy(admin);
        Notification saved = notifications.save(notification);
        auditLogs.logInTransaction(
                username,
                "SEND_NOTIFICATION",
                null,
                null,
                username + " gửi thông báo chung: " + saved.getTitle());
        return response(saved, admin);
    }

    @Transactional
    public void delete(Long id, String username) {
        User actor = users.findByUsernameIgnoreCase(username).orElseThrow();
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
        boolean allowed = notification.getRecipientId() == null
                ? "ADMIN".equalsIgnoreCase(actor.getRole())
                : java.util.Objects.equals(notification.getRecipientId(), actor.getId());
        if (!allowed) throw new AccessDeniedException("Bạn không có quyền xóa thông báo này");
        auditLogs.logInTransaction(username, "DELETE_NOTIFICATION", null, null,
                com.clouddocs.auth.ProfilePresentation.name(actor) + " đã xóa thông báo: " + notification.getTitle());
        notifications.delete(notification);
    }

    @Transactional
    public void sendTaskNotification(User sender, User recipient, Long taskId, String title, String message) {
        Notification notification = new Notification();
        notification.setTitle(title.substring(0,title.offsetByCodePoints(0,Math.min(150,title.codePointCount(0,title.length())))));
        notification.setMessage(message.substring(0,message.offsetByCodePoints(0,Math.min(1000,message.codePointCount(0,message.length())))));
        notification.setCreatedBy(sender);notification.setRecipientId(recipient.getId());notification.setWorkTaskId(taskId);
        notifications.save(notification);
    }

    private NotificationResponse response(Notification value, User actor) {
        return new NotificationResponse(
                value.getId(),
                value.getTitle(),
                value.getMessage(),
                com.clouddocs.auth.ProfilePresentation.name(value.getCreatedBy()),
                value.getCreatedAt(),
                value.getWorkTaskId(),
                value.getRecipientId() == null ? "ADMIN".equalsIgnoreCase(actor.getRole()) : java.util.Objects.equals(value.getRecipientId(),actor.getId()));
    }
}
