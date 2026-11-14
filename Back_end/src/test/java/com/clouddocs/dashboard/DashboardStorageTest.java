package com.clouddocs.dashboard;

import com.clouddocs.audit.AuditLogRepository;
import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.Document;
import com.clouddocs.document.DocumentRepository;
import com.clouddocs.storage.StorageQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardStorageTest {
    @org.junit.jupiter.api.BeforeEach
    void confirmedFixtures() {
        when(documents.findUploadConfirmedIds(anyCollection())).thenAnswer(call -> new java.util.ArrayList<>((java.util.Collection<Long>) call.getArgument(0)));
    }
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StorageQuotaService quotas = spy(new StorageQuotaService(documents, users, 1000, 10000, 100));
    private final Authentication auth = mock(Authentication.class);
    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final DashboardController controller =
            new DashboardController(documents, users, auditLogs, quotas);

    private User user(Long id, String role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername("account" + id);
        user.setRole(role);
        return user;
    }

    private Document file(Long owner, long size) {
        Document file = new Document();
        ReflectionTestUtils.setField(file, "id", owner);
        file.setOwnerId(owner);
        file.setFileSize(size);
        file.setFileType("PDF");
        return file;
    }

    @Test void adminDistributionIncludesAllActiveOwnersAndTypesEvenWithoutUploadAudit() {
        User admin=user(1L,"ADMIN");
        Document txt=file(1L,3300L), png=file(3L,1050000L), docx=file(4L,37740L), zero=file(5L,0L), trash=file(6L,500L);
        txt.setFileType("TXT");png.setFileType("PNG");docx.setFileType("DOCX");zero.setFileType("ZIP");trash.setStatus("TRASHED");
        when(auth.getName()).thenReturn("account1");when(users.findByUsernameIgnoreCase("account1")).thenReturn(Optional.of(admin));
        when(users.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(admin));
        when(documents.findAll()).thenReturn(List.of(txt,png,docx,zero,trash));
        when(documents.findUploadConfirmedIds(anyCollection())).thenReturn(List.of());
        var summary=controller.summary(auth);var distribution=(Map<?,?>)summary.get("storageDistribution");
        assertEquals("SYSTEM",distribution.get("scope"));assertEquals(1091040L,distribution.get("usedBytes"));
        assertEquals(Map.of("TXT",3300L,"PNG",1050000L,"DOCX",37740L,"ZIP",0L),distribution.get("storageByType"));
        assertEquals(4,distribution.get("documentCount"));assertEquals(1,distribution.get("zeroSizeCount"));
        assertEquals(3300L,((StorageQuotaService.Usage)summary.get("storageQuota")).usedBytes());
    }
    @Test void userDistributionExcludesSharedFilesAndReportsUnknownSizes() {
        User account=user(3L,"USER");Document owned=file(3L,100L), shared=file(4L,900L), unknown=file(3L,0L);
        unknown.setFileSize(null);
        when(auth.getName()).thenReturn("account3");when(users.findByUsernameIgnoreCase("account3")).thenReturn(Optional.of(account));
        when(documents.findAccessibleByUserId(3L)).thenReturn(List.of(owned,shared,unknown));
        when(documents.findByOwnerId(3L)).thenReturn(List.of(owned,unknown));
        var distribution=(Map<?,?>)controller.summary(auth).get("storageDistribution");
        assertEquals("PERSONAL",distribution.get("scope"));assertEquals(100L,distribution.get("usedBytes"));
        assertEquals(Map.of("PDF",100L),distribution.get("storageByType"));assertEquals(1,distribution.get("missingSizeCount"));
    }
    @Test void recentDashboardDocumentsShowActualOwnersNameInsteadOfAdminOrCognitoCode() {
        User admin=user(1L,"ADMIN"), owner=user(3L,"USER");admin.setFullName("Admin name");owner.setFullName("Trần Hải Lương");
        Document document=file(3L,200L);document.setOwner("c_opaque-sub");
        when(auth.getName()).thenReturn("account1");when(users.findByUsernameIgnoreCase("account1")).thenReturn(Optional.of(admin));
        when(users.findAllById(List.of(3L))).thenReturn(List.of(owner));
        when(users.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(admin,owner));
        when(documents.findAll()).thenReturn(List.of(document));
        var summary=controller.summary(auth);
        var recent=(List<?>)summary.get("recentDocuments");
        assertEquals("Trần Hải Lương",((Map<?,?>)recent.getFirst()).get("owner"));
        assertEquals("c_opaque-sub",document.getOwner());
    }
    @Test void trashIsHiddenFromDashboardButIncludedInAdminStorage() {
        User admin=user(1L,"ADMIN");Document active=file(1L,100L), trash=file(3L,200L), purged=file(4L,0L);
        trash.setStatus("TRASHED");purged.setStatus("PURGED");
        when(auth.getName()).thenReturn("account1");when(users.findByUsernameIgnoreCase("account1")).thenReturn(Optional.of(admin));
        when(users.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(admin));
        when(documents.findAll()).thenReturn(List.of(active,trash,purged));
        var summary=controller.summary(auth);
        assertEquals(1,summary.get("totalDocuments"));assertEquals(300L,summary.get("systemStorageBytes"));
        assertEquals(1,((List<?>)summary.get("recentDocuments")).size());
    }
    @Test
    void sharedFilesStayVisibleButDoNotConsumePersonalStorage() {
        User user = user(3L, "USER");
        Document owned = file(3L, 20L);
        when(auth.getName()).thenReturn("account3");
        when(users.findByUsernameIgnoreCase("account3")).thenReturn(Optional.of(user));
        when(documents.findAccessibleByUserId(3L)).thenReturn(List.of(owned, file(4L, 500L)));
        when(documents.findByOwnerId(3L)).thenReturn(List.of(owned));
        when(auditLogs.countByActionAndUserId("DOWNLOAD", 3L)).thenReturn(7L);
        var summary = controller.summary(auth);
        assertEquals(2, summary.get("totalDocuments"));
        assertEquals(20L, summary.get("storageBytes"));
        assertEquals(Map.of("PDF", 20L), summary.get("storageByType"));
        assertFalse(summary.containsKey("storageUsers"));
        assertEquals(7L, summary.get("downloads"));
        verify(auditLogs, never()).countByAction(anyString());
    }

    @Test
    void adminSeparatesPersonalQuotaFromSystemStorage() {
        User admin = user(1L, "ADMIN");
        User user = user(3L, "USER");
        when(auth.getName()).thenReturn("account1");
        when(users.findByUsernameIgnoreCase("account1")).thenReturn(Optional.of(admin));
        when(users.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(admin, user));
        when(documents.findAll()).thenReturn(List.of(file(1L, 100L), file(3L, 200L)));
        when(quotas.limitFor(admin)).thenReturn(10000L);
        when(quotas.limitFor(user)).thenReturn(1000L);
        when(auditLogs.countByAction("DOWNLOAD")).thenReturn(42L);
        var summary = controller.summary(auth);
        assertEquals(100L, summary.get("storageBytes"));
        assertEquals(300L, summary.get("systemStorageBytes"));
        assertEquals(42L, summary.get("downloads"));
        verify(auditLogs, never()).countByActionAndUserId(anyString(), anyLong());
        var rows = (List<?>) summary.get("storageUsers");
        assertEquals(2, rows.size());
        assertEquals(200L, ((Map<?, ?>) rows.get(1)).get("usedBytes"));
    }

    @Test
    void userWithNoDownloadsDoesNotReceiveGlobalDownloadCount() {
        User user = user(4L, "USER");
        when(auth.getName()).thenReturn("account4");
        when(users.findByUsernameIgnoreCase("account4")).thenReturn(Optional.of(user));
        when(documents.findAccessibleByUserId(4L)).thenReturn(List.of());
        when(documents.findByOwnerId(4L)).thenReturn(List.of());
        when(auditLogs.countByActionAndUserId("DOWNLOAD", 4L)).thenReturn(0L);
        assertEquals(0L, controller.summary(auth).get("downloads"));
        verify(auditLogs, never()).countByAction(anyString());
    }

 @Test void sharedDocumentsCannotExposeOtherUsersActivityInDashboard(){User user=user(2L,"USER");when(auth.getName()).thenReturn("account2");when(users.findByUsernameIgnoreCase("account2")).thenReturn(Optional.of(user));when(documents.findAccessibleByUserId(2L)).thenReturn(List.of(file(2L,20),file(3L,30)));when(documents.findByOwnerId(2L)).thenReturn(List.of(file(2L,20)));var event=new com.clouddocs.audit.AuditLog();event.setUser(user);event.setAction("FAVORITE_ADD");event.setDetails("Own activity");when(auditLogs.findTop20ByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(event));var summary=controller.summary(auth);var activity=(List<?>)summary.get("recentActivity");assertEquals(1,activity.size());assertEquals("account2",((com.clouddocs.audit.AuditLogController.AuditLogResponse)activity.getFirst()).username());verify(auditLogs,never()).findTop20ByOrderByCreatedAtDesc();}

}
