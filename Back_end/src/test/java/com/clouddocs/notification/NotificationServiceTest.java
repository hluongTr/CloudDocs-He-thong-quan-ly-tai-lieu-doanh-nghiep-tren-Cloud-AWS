package com.clouddocs.notification;

import com.clouddocs.audit.AuditLogService;
import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditLogService auditLogs = mock(AuditLogService.class);
    private final NotificationService service =
            new NotificationService(notifications, users, auditLogs);

    @Test
    void adminCanSendNotificationToAllUsers() {
        User admin = user("admin", "ADMIN");
        admin.setFullName("Trần Hải Lương");
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result =
                service.create(
                        "admin",
                        new NotificationDtos.CreateNotificationRequest(
                                " Bảo trì ", " Hệ thống bảo trì lúc 22:00 "));
        assertEquals("Bảo trì", result.title());
        assertEquals("Hệ thống bảo trì lúc 22:00", result.message());
        assertEquals("Trần Hải Lương", result.createdBy());
        verify(notifications).save(any(Notification.class));
        verify(auditLogs)
                .logInTransaction(eq("admin"), eq("SEND_NOTIFICATION"), isNull(), isNull(), contains("Bảo trì"));
    }

    @Test
    void regularUserCannotSendGlobalNotification() {
        when(users.findByUsernameIgnoreCase("user1"))
                .thenReturn(Optional.of(user("user1", "USER")));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        service.create(
                                "user1",
                                new NotificationDtos.CreateNotificationRequest(
                                        "Tin mới", "Nội dung")));
        verify(notifications, never()).save(any());
    }

    @Test void regularUserCannotDeleteGlobalAnnouncement() {
        when(users.findByUsernameIgnoreCase("user1")).thenReturn(Optional.of(user("user1", "USER")));
        when(notifications.findById(1L)).thenReturn(Optional.of(new Notification()));
        assertThrows(AccessDeniedException.class, () -> service.delete(1L, "user1"));
        verify(notifications, never()).delete(any()); verifyNoInteractions(auditLogs);
    }

    @Test void adminCanDeleteAnnouncementAndAuditItsTitle() {
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user("admin", "ADMIN")));
        Notification notification = new Notification(); notification.setTitle("Maintenance");
        when(notifications.findById(1L)).thenReturn(Optional.of(notification));
        service.delete(1L, "admin"); verify(notifications).delete(notification);
        verify(auditLogs).logInTransaction(eq("admin"), eq("DELETE_NOTIFICATION"), isNull(), isNull(), contains("Maintenance"));
    }

    @Test void oldNotificationsUseCurrentProfileNameWithoutExposingCognitoId() {
        when(users.findByUsernameIgnoreCase("reader")).thenReturn(Optional.of(user("reader","USER")));
        User sender = user("c_opaque-cognito-sub", "ADMIN");
        sender.setFullName("Trần Hải Lương");
        Notification notification = new Notification();
        notification.setTitle("Update"); notification.setMessage("Message"); notification.setCreatedBy(sender);
        when(notifications.findVisible(any(),any())).thenReturn(java.util.List.of(notification));
        assertEquals("Trần Hải Lương", service.list("reader").getFirst().createdBy());
        sender.setFullName("Tên đã cập nhật");
        assertEquals("Tên đã cập nhật", service.list("reader").getFirst().createdBy());
        sender.setFullName(null);
        assertEquals(com.clouddocs.auth.ProfilePresentation.name(sender), service.list("reader").getFirst().createdBy());
        assertNotEquals(sender.getUsername(), service.list("reader").getFirst().createdBy());
    }

    private User user(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }
    @Test void notificationListingUsesRecipientFilterBeforeTop50Limit() {
        User reader=user("reader","USER");org.springframework.test.util.ReflectionTestUtils.setField(reader,"id",2L);
        when(users.findByUsernameIgnoreCase("reader")).thenReturn(Optional.of(reader));
        Notification global=new Notification();global.setCreatedBy(user("admin","ADMIN"));
        Notification personal=new Notification();personal.setCreatedBy(user("admin","ADMIN"));personal.setRecipientId(2L);personal.setWorkTaskId(8L);
        when(notifications.findVisible(eq(2L),any())).thenReturn(java.util.List.of(global,personal));
        var result=service.list("reader");assertFalse(result.get(0).canDelete());assertTrue(result.get(1).canDelete());assertEquals(8L,result.get(1).workTaskId());
        verify(notifications).findVisible(eq(2L),argThat(page->page.getPageSize()==50));
    }
    @Test void recipientCanDismissPersonalNotificationButOtherUsersIncludingAdminCannot() {
        User worker=user("worker","USER");org.springframework.test.util.ReflectionTestUtils.setField(worker,"id",2L);
        User admin=user("admin","ADMIN");org.springframework.test.util.ReflectionTestUtils.setField(admin,"id",1L);
        when(users.findByUsernameIgnoreCase("worker")).thenReturn(Optional.of(worker));when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        Notification personal=new Notification();personal.setRecipientId(2L);personal.setTitle("Task");when(notifications.findById(5L)).thenReturn(Optional.of(personal));
        assertThrows(AccessDeniedException.class,()->service.delete(5L,"admin"));
        service.delete(5L,"worker");verify(notifications).delete(personal);
    }
}
