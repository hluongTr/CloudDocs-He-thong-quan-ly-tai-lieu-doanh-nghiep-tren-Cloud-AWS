package com.clouddocs.document;

import com.clouddocs.audit.*;
import com.clouddocs.auth.*;
import com.clouddocs.permission.*;
import com.clouddocs.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentOwnerNamesTest {
    private Document createUploadedDocument(String suppliedDepartment) {
        when(documents.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved=invocation.getArgument(0);
            ReflectionTestUtils.setField(saved,"id",11L);
            ReflectionTestUtils.setField(saved,"updatedAt",Instant.now());
            return saved;
        });
        service.create(new DocumentDtos.CreateDocumentRequest("report.pdf","Description","report.pdf","PDF",10L,suppliedDepartment),"admin-sub");
        var captured=org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documents).save(captured.capture());return captured.getValue();
    }
    @Test void uploadUsesCurrentAccountDepartmentAndIgnoresSpoofedDepartment() {
        admin.setDepartment("Human Resources");
        Document uploaded=createUploadedDocument("Finance");
        assertEquals("Human Resources",uploaded.getDepartment());assertEquals("report.pdf",uploaded.getTitle());
    }
    @Test void uploadAcceptsOmittedDepartmentWithUnassignedProfile() {
        admin.setDepartment(null);
        assertEquals(com.clouddocs.department.DepartmentService.UNASSIGNED,createUploadedDocument(null).getDepartment());
    }
    final DocumentRepository documents = mock(DocumentRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final AuditLogRepository audit = mock(AuditLogRepository.class);
    final DocumentPermissionService permissions = mock(DocumentPermissionService.class);
    final DocumentService service = new DocumentService(documents, mock(S3StorageService.class),
            mock(LocalFileStorageService.class), users, permissions, mock(AuditLogService.class),
            audit, mock(StorageQuotaService.class), mock(com.clouddocs.work.WorkTaskRepository.class), mock(com.clouddocs.department.DepartmentService.class), "bucket");
    User admin, owner;
    Document doc;
    @BeforeEach void setup() {
        admin = new User();admin.setUsername("admin-sub");admin.setRole("ADMIN");admin.setFullName("Quản trị viên");
        ReflectionTestUtils.setField(admin,"id",1L);
        owner = new User();owner.setUsername("c_opaque-owner-sub");owner.setFullName("Trần Hải Lương");
        ReflectionTestUtils.setField(owner,"id",2L);
        doc = new Document();doc.setOwner(owner.getUsername());doc.setOwnerId(2L);doc.setTitle("Report");
        doc.setFileName("report.pdf");doc.setFileType("PDF");doc.setDepartment("IT");
        ReflectionTestUtils.setField(doc,"id",10L);ReflectionTestUtils.setField(doc,"updatedAt",Instant.now());
        when(users.findByUsernameIgnoreCase("admin-sub")).thenReturn(Optional.of(admin));
        when(users.findAllById(List.of(2L))).thenReturn(List.of(owner));
        when(documents.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(doc));
        when(permissions.canView(doc,"admin-sub",true)).thenReturn(true);
    }
    @Test void documentListsAndSearchUseProfileNameWithoutChangingStoredOwnership() {
        assertEquals("Trần Hải Lương", service.findAll(null,null,null,"admin-sub").getFirst().owner());
        assertEquals(1,service.findAll("hải lương",null,null,"admin-sub").size());
        assertEquals("c_opaque-owner-sub",doc.getOwner());assertEquals(2L,doc.getOwnerId());
        verify(permissions,atLeastOnce()).effectiveAccess(doc,admin);
    }
    @Test void sharedAndRecentCollectionsUseSameOwnerProfile() {
        when(documents.findSharedByUserId(1L)).thenReturn(List.of(doc));
        AuditLog log = new AuditLog();log.setDocument(doc);
        when(audit.findRecentDocumentActivity(eq(1L),anyList())).thenReturn(List.of(log));
        assertEquals("Trần Hải Lương",service.findShared("admin-sub").getFirst().owner());
        assertEquals("Trần Hải Lương",service.findRecent("admin-sub").getFirst().owner());
    }
    @Test void renamedProfileIsShownOnNextLoadEvenForExistingDocuments() {
        owner.setFullName("Tên mới");
        assertEquals("Tên mới",service.findAll(null,null,null,"admin-sub").getFirst().owner());
    }
    @Test void missingOwnerNeverLeaksOpaqueUsernameAndOwnerQueriesAreBatched() {
        when(users.findAllById(List.of(2L))).thenReturn(List.of());
        assertEquals("Chưa cập nhật",service.findAll(null,null,null,"admin-sub").getFirst().owner());
        verify(users).findAllById(List.of(2L));verify(users,never()).findById(anyLong());
    }
}
