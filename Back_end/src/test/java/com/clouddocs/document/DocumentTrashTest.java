package com.clouddocs.document;

import com.clouddocs.audit.*;
import com.clouddocs.auth.*;
import com.clouddocs.permission.*;
import com.clouddocs.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentTrashTest {
    DocumentRepository documents = mock(DocumentRepository.class);
    UserRepository users = mock(UserRepository.class);
    S3StorageService s3 = mock(S3StorageService.class);
    LocalFileStorageService local = mock(LocalFileStorageService.class);
    AuditLogService audit = mock(AuditLogService.class);
    DocumentPermissionService permissions = mock(DocumentPermissionService.class);
    DocumentService service = new DocumentService(documents, s3, local, users, permissions,
            audit, mock(AuditLogRepository.class), mock(StorageQuotaService.class), mock(com.clouddocs.work.WorkTaskRepository.class), "bucket");
    Document doc;
    User owner;

    @BeforeEach void setup() {
        owner = new User(); owner.setUsername("cognito-opaque-id"); owner.setFullName("Trần Hải Lương"); owner.setRole("USER");
        ReflectionTestUtils.setField(owner, "id", 1L);
        doc = new Document(); doc.setTitle("Báo cáo.pdf"); doc.setOwnerId(1L); doc.setOwner(owner.getUsername());
        doc.setS3Key("documents/report"); doc.setFileSize(50L);
        ReflectionTestUtils.setField(doc, "id", 9L);
        ReflectionTestUtils.setField(doc, "updatedAt", Instant.now());
        when(documents.findById(9L)).thenReturn(Optional.of(doc));
        when(documents.findForUpdate(9L)).thenReturn(Optional.of(doc));
        when(users.findByUsernameIgnoreCase(owner.getUsername())).thenReturn(Optional.of(owner));
        when(permissions.isOwner(doc, owner.getUsername())).thenReturn(true);
    }
    @Test void softDeleteRetainsFileAndPermissionsAndAuditsName() {
        service.delete(9L, owner.getUsername());
        assertTrue(doc.isTrashed()); assertEquals(50L, doc.getFileSize());
        verifyNoInteractions(s3, local);
        verify(permissions, never()).deleteAllForDocument(any());
        verify(documents, never()).delete(any(Document.class));
        verify(audit).logInTransaction(eq(owner.getUsername()), eq("DELETE"), eq(9L), isNull(), contains("Trần Hải Lương"));
    }
    @Test void ownerCanRestoreWithoutLosingSharedPermissions() {
        doc.setStatus("TRASHED"); service.restore(9L, owner.getUsername());
        assertEquals("ACTIVE", doc.getStatus()); verifyNoInteractions(s3, local);
        verify(audit).logInTransaction(eq(owner.getUsername()), eq("RESTORE"), eq(9L), isNull(), contains(doc.getTitle()));
    }
    @Test void anotherUserCannotDeleteRestoreOrPurge() {
        when(permissions.isOwner(doc, owner.getUsername())).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.delete(9L, owner.getUsername()));
        doc.setStatus("TRASHED");
        assertThrows(AccessDeniedException.class, () -> service.restore(9L, owner.getUsername()));
        assertThrows(AccessDeniedException.class, () -> service.purge(9L, owner.getUsername()));
        verifyNoInteractions(s3, local, audit);
    }
    @Test void adminCanRestoreAnotherOwnersDocument() {
        owner.setRole("ADMIN"); when(permissions.isOwner(doc, owner.getUsername())).thenReturn(false);
        doc.setStatus("TRASHED"); service.restore(9L, owner.getUsername()); assertTrue(doc.isAvailable());
    }
    @Test void purgeRetainsAuditReferenceAndReleasesStorageOnlyAfterS3Succeeds() {
        doc.setStatus("TRASHED"); service.purge(9L, owner.getUsername());
        assertTrue(doc.isPurged()); assertEquals(0L, doc.getFileSize());
        verify(s3).deleteIfExists("bucket", "documents/report");
        verify(local).deleteIfExists("documents/report");
        verify(permissions).deleteAllForDocument(9L); verify(documents, never()).delete(any(Document.class));
        verify(audit).logInTransaction(eq(owner.getUsername()), eq("PURGE"), eq(9L), isNull(), contains(doc.getTitle()));
    }
    @Test void failedS3PurgeRemainsRecoverableAndDoesNotAuditSuccess() {
        doc.setStatus("TRASHED"); doThrow(new IllegalStateException("S3 unavailable")).when(s3).deleteIfExists(anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> service.purge(9L, owner.getUsername()));
        assertTrue(doc.isTrashed()); assertEquals(50L, doc.getFileSize()); verifyNoInteractions(audit, local);
    }
    @Test void permanentDeleteRequiresTrashAndPurgedCannotBeRestored() {
        assertThrows(IllegalArgumentException.class, () -> service.purge(9L, owner.getUsername()));
        doc.setStatus("PURGED"); assertThrows(IllegalArgumentException.class, () -> service.restore(9L, owner.getUsername()));
        verifyNoInteractions(s3, local, audit);
    }
    @Test void trashListingIsScopedToOwnerWhileAdminSeesAll() {
        doc.setStatus("TRASHED"); when(documents.findByOwnerId(1L)).thenReturn(List.of(doc));
        assertEquals(1, service.findTrash(owner.getUsername()).size()); verify(documents, never()).findAll();
        owner.setRole("ADMIN"); when(documents.findAll()).thenReturn(List.of(doc));
        assertEquals(1, service.findTrash(owner.getUsername()).size());
    }
    @Test void trashBlocksEvenAdminAndSharedPermissions() {
        var realPermissions = new DocumentPermissionService(mock(DocumentPermissionRepository.class), documents, users, audit);
        for (String status : List.of("TRASHED", "PURGED")) {
            doc.setStatus(status);
            assertFalse(realPermissions.canView(doc, owner.getUsername(), true));
            assertFalse(realPermissions.canDownload(doc, owner.getUsername(), true));
            assertFalse(realPermissions.canEdit(doc, owner.getUsername(), true));
            assertThrows(AccessDeniedException.class, () -> realPermissions.grantOrUpdate(9L, 2L,
                    new DocumentPermissionDtos.PermissionRequest(DocumentPermissionType.VIEW), owner.getUsername()));
        }
    }
    @Test void submittedAndApprovedReportsCannotBeTrashedOrOverwritten() {
        var taskRepository=mock(com.clouddocs.work.WorkTaskRepository.class);
        var guarded=new DocumentService(documents,s3,local,users,permissions,audit,mock(AuditLogRepository.class),mock(StorageQuotaService.class),taskRepository,"bucket");
        when(taskRepository.existsByReportDocumentIdAndStatusIn(eq(9L),anyCollection())).thenReturn(true);
        when(permissions.canEdit(doc,owner.getUsername(),false)).thenReturn(true);
        assertThrows(IllegalStateException.class,()->guarded.delete(9L,owner.getUsername()));
        assertThrows(IllegalStateException.class,()->guarded.createUploadUrl(9L,owner.getUsername()));
        assertEquals("ACTIVE",doc.getStatus());verifyNoInteractions(s3,local,audit);
    }
}
