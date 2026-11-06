package com.clouddocs.document;

import com.clouddocs.audit.AuditLogRepository;
import com.clouddocs.audit.AuditLogService;
import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.permission.DocumentPermissionService;
import com.clouddocs.storage.LocalFileStorageService;
import com.clouddocs.storage.S3StorageService;
import com.clouddocs.storage.StorageQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import java.net.URI;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentQuotaTest {
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final S3StorageService s3 = mock(S3StorageService.class);
    private final LocalFileStorageService local = mock(LocalFileStorageService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DocumentPermissionService permissions = mock(DocumentPermissionService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final StorageQuotaService quotas = mock(StorageQuotaService.class);
    private final DocumentService service =
            new DocumentService(
                    documents,
                    s3,
                    local,
                    users,
                    permissions,
                    audit,
                    mock(AuditLogRepository.class),
                    quotas,
                    mock(com.clouddocs.work.WorkTaskRepository.class), "bucket");
    private Document document;

    @BeforeEach
    void setup() {
        User editor = new User();
        editor.setUsername("editor");
        ReflectionTestUtils.setField(editor, "id", 4L);
        document = new Document();
        ReflectionTestUtils.setField(document, "id", 9L);
        document.setOwnerId(3L);
        document.setS3Key("documents/file.pdf");
        document.setFileSize(50L);
        document.setFileType("PDF");
        when(users.findByUsernameIgnoreCase("editor")).thenReturn(Optional.of(editor));
        when(documents.findById(9L)).thenReturn(Optional.of(document));
        when(documents.findForUpdate(9L)).thenReturn(Optional.of(document));
        when(permissions.canEdit(document, "editor", false)).thenReturn(true);
    }

    @Test
    void rejectsMetadataBeforeSavingOrAuditingWhenQuotaIsFull() {
        doThrow(new IllegalStateException("full")).when(quotas).requireCapacity(4L, null, 50L);
        var request =
                new DocumentDtos.CreateDocumentRequest("file", "", "file.pdf", "PDF", 50L, "IT");
        assertThrows(IllegalStateException.class, () -> service.create(request, "editor"));
        verify(documents, never()).save(any());
        verifyNoInteractions(audit, s3);
    }

    @Test
    void editorUploadUsesOwnersQuotaAndSignsReservedSize() throws Exception {
        when(s3.createUploadUrl("bucket", "documents/file.pdf", "PDF", 50L))
                .thenReturn(
                        new S3StorageService.PresignedResult(
                                URI.create("https://example.test").toURL(), 900));
        service.createUploadUrl(9L, "editor");
        var order = inOrder(quotas, s3);
        order.verify(quotas).requireCapacity(3L, 9L, 50L);
        order.verify(s3).createUploadUrl("bucket", "documents/file.pdf", "PDF", 50L);
    }

    @Test
    void cannotGetUploadUrlWithoutEditPermission() {
        when(permissions.canEdit(document, "editor", false)).thenReturn(false);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.createUploadUrl(9L, "editor"));
        verifyNoInteractions(quotas, s3);
    }

    @Test
    void localFileIsNotWrittenWhenQuotaRejectsReplacement() {
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[100]);
        doThrow(new IllegalStateException("full")).when(quotas).requireCapacity(3L, 9L, 100L);
        assertThrows(
                IllegalStateException.class, () -> service.uploadLocalFile(9L, file, "editor"));
        verifyNoInteractions(local);
        verify(documents, never()).save(any());
    }

    @Test
    void completionChecksActualS3SizeInsteadOfTrustingClient() {
        when(s3.headObject("bucket", "documents/file.pdf"))
                .thenReturn(HeadObjectResponse.builder().contentLength(80L).build());
        service.completeS3Upload(9L, "editor");
        verify(quotas).requireCapacity(3L, 9L, 80L);
        assertEquals(80L, document.getFileSize());
        verify(documents).save(document);
    }

    @Test
    void failedCompletionDoesNotSaveOversizeMetadataOrEmitSuccessAudit() {
        when(s3.headObject("bucket", "documents/file.pdf"))
                .thenReturn(HeadObjectResponse.builder().contentLength(200L).build());
        doThrow(new IllegalArgumentException("oversize"))
                .when(quotas)
                .requireCapacity(3L, 9L, 200L);
        assertThrows(IllegalArgumentException.class, () -> service.completeS3Upload(9L, "editor"));
        assertEquals(50L, document.getFileSize());
        verify(documents, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void emptyFileReleasesOldSizeAndAuditWaitsForCommit() {
        when(s3.headObject("bucket", "documents/file.pdf"))
                .thenReturn(HeadObjectResponse.builder().contentLength(0L).build());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.completeS3Upload(9L, "editor");
            assertEquals(0L, document.getFileSize());
            verifyNoInteractions(audit);
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.forEach(callback -> callback.afterCommit());
            verify(audit).log(eq("editor"), eq("UPLOAD"), eq(9L), isNull(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
    @Test void staleUploadSigningCannotResurrectDocumentTrashedWhileWaitingForQuotaLock() {
        doAnswer(call -> { document.setStatus("TRASHED"); return null; }).when(documents).refreshState(document);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.createUploadUrl(9L,"editor"));
        verifyNoInteractions(s3,audit);verify(documents,never()).save(any(Document.class));
    }
    @Test void staleUploadCompletionCannotResurrectPurgedDocument() {
        when(s3.headObject("bucket","documents/file.pdf")).thenReturn(HeadObjectResponse.builder().contentLength(20L).build());
        doAnswer(call -> { document.setStatus("PURGED"); return null; }).when(documents).refreshState(document);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.completeS3Upload(9L,"editor"));
        verify(documents,never()).save(any(Document.class));verifyNoInteractions(audit);
    }
    @Test void metadataEditAuditsInsideBusinessTransactionAndPreservesOwnership() {
        when(documents.save(document)).thenReturn(document);
        ReflectionTestUtils.setField(document,"updatedAt",java.time.Instant.now());
        service.update(9L,new DocumentDtos.UpdateDocumentRequest("New report","","IT","ACTIVE"),"editor");
        verify(audit).logInTransaction(eq("editor"),eq("EDIT"),eq(9L),isNull(),anyString());
        verify(audit,never()).log(any(),any(),any(),any(),any());assertEquals(3L,document.getOwnerId());
    }
    @Test void sameNameCreatesUseShortDistinctKeysEvenForLongUnicodeFilename() {
        var user=users.findByUsernameIgnoreCase("editor").orElseThrow();
        when(documents.save(any(Document.class))).thenAnswer(call -> {
            Document d=call.getArgument(0);ReflectionTestUtils.setField(d,"id",100L);ReflectionTestUtils.setField(d,"updatedAt",java.time.Instant.now());return d;
        });
        var request=new DocumentDtos.CreateDocumentRequest("Report","","ộ".repeat(250)+".txt","TXT",1L,"IT");
        service.create(request,"editor");service.create(request,"editor");
        var captured=org.mockito.ArgumentCaptor.forClass(Document.class);verify(documents,times(2)).save(captured.capture());
        var values=captured.getAllValues();assertNotEquals(values.get(0).getS3Key(),values.get(1).getS3Key());
        assertTrue(values.get(0).getS3Key().length()<100);
    }
}
