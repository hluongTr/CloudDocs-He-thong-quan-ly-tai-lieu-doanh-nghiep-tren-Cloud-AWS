package com.clouddocs.folder;

import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import com.clouddocs.audit.AuditLogService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FolderServiceTest {
    final DocumentFolderRepository folders = mock(DocumentFolderRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final DocumentRepository documents = mock(DocumentRepository.class);
    final DocumentService documentService = mock(DocumentService.class);
    final AuditLogService audit = mock(AuditLogService.class);
    final FolderService service = new FolderService(folders,users,documents,documentService,audit);
    @BeforeEach void setup() {
        User u=new User();ReflectionTestUtils.setField(u,"id",1L);u.setUsername("owner");
        when(users.findByUsernameIgnoreCase("owner")).thenReturn(Optional.of(u));
        when(users.findForStorageUpdate(1L)).thenReturn(Optional.of(u));
        when(folders.save(any())).thenAnswer(i->i.getArgument(0));
    }
    DocumentFolder folder(long id,long owner,Long parent,String name) {
        DocumentFolder f=new DocumentFolder();ReflectionTestUtils.setField(f,"id",id);f.setOwnerId(owner);f.setParentId(parent);f.setName(name);
        when(folders.findById(id)).thenReturn(Optional.of(f));return f;
    }
    Document document(long id,long owner,Long folder,String status) {
        Document d=new Document();ReflectionTestUtils.setField(d,"id",id);d.setOwnerId(owner);d.setFolderId(folder);d.setStatus(status);d.setTitle("Report");
        when(documents.findForUpdate(id)).thenReturn(Optional.of(d));return d;
    }
    @Test void createsNestedFolderForOwner() {
        folder(10,1,null,"Project");var f=service.create(" Reports ",10L,"owner");
        assertEquals("Reports",f.getName());assertEquals(10L,f.getParentId());assertEquals(1L,f.getOwnerId());
    }
    @Test void foreignParentIsForbidden() {
        folder(10,2,null,"Private");assertThrows(AccessDeniedException.class,()->service.create("New",10L,"owner"));verify(folders,never()).save(any());
    }
    @Test void duplicateSiblingIsRejectedButSameNameInOtherParentAllowed() {
        var f=folder(10,1,null,"Reports");when(folders.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of(f));
        assertThrows(IllegalArgumentException.class,()->service.create(" reports ",null,"owner"));
        assertDoesNotThrow(()->service.create("Reports",10L,"owner"));
    }
    @Test void invalidNamesAreRejected() {
        for(String name:List.of(" ","a/b","a\\b","a\nb","a".repeat(121)))assertThrows(IllegalArgumentException.class,()->service.create(name,null,"owner"));
    }
    @Test void renameCannotAccessForeignFolder() {
        folder(10,2,null,"Private");assertThrows(AccessDeniedException.class,()->service.rename(10L,"New","owner"));
    }
    @Test void movesMultipleOwnedDocumentsWithoutChangingKeysOrStatus() {
        folder(10,1,null,"Project");var a=document(1,1,null,"ACTIVE");a.setS3Key("original-key");var b=document(2,1,null,"SHARED");
        service.move(List.of(1L,2L),10L,"owner");assertEquals(10L,a.getFolderId());assertEquals(10L,b.getFolderId());assertEquals("original-key",a.getS3Key());assertEquals("SHARED",b.getStatus());
    }
    @Test void sharedFilesAndForeignDestinationCannotBeOrganized() {
        document(1,2,null,"ACTIVE");assertThrows(AccessDeniedException.class,()->service.move(List.of(1L),null,"owner"));
        folder(10,2,null,"Private");assertThrows(AccessDeniedException.class,()->service.move(List.of(1L),10L,"owner"));
    }
    @Test void trashedFilesCannotBeMoved() {
        document(1,1,null,"TRASHED");assertThrows(IllegalArgumentException.class,()->service.move(List.of(1L),null,"owner"));
    }
    @Test void deleteTraversesDescendantsAndDetachesAlreadyTrashedFiles() {
        var root=folder(10,1,null,"Project");var child=folder(11,1,10L,"Reports");var other=folder(12,1,null,"Other");
        when(folders.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of(root,child,other));
        var a=document(1,1,11L,"ACTIVE");var b=document(2,1,10L,"TRASHED");var c=document(3,1,12L,"ACTIVE");
        when(documents.findByOwnerId(1L)).thenReturn(List.of(a,b,c));
        service.delete(10L,"owner");verify(documentService).delete(1L,"owner");verify(documentService,never()).delete(2L,"owner");verify(documentService,never()).delete(3L,"owner");
        assertNull(a.getFolderId());assertNull(b.getFolderId());assertEquals(12L,c.getFolderId());
        verify(folders).deleteAll(List.of(root,child));
    }
    @Test void protectedReportAbortsFolderRemoval() {
        var root=folder(10,1,null,"Project");when(folders.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of(root));
        var report=document(1,1,10L,"ACTIVE");when(documents.findByOwnerId(1L)).thenReturn(List.of(report));
        doThrow(new IllegalStateException("Report locked")).when(documentService).delete(1L,"owner");
        assertThrows(IllegalStateException.class,()->service.delete(10L,"owner"));verify(folders,never()).deleteAll(any());assertEquals(10L,report.getFolderId());
    }
    @Test void foreignFolderCannotBeDeletedEvenById() {
        folder(10,2,null,"Private");assertThrows(AccessDeniedException.class,()->service.delete(10L,"owner"));verifyNoInteractions(documentService);
    }
    @Test void adminBrowsesAllFolderTreesWithOwnerNames() {
        User admin=users.findByUsernameIgnoreCase("owner").orElseThrow();admin.setRole("ADMIN");
        User employee=new User();ReflectionTestUtils.setField(employee,"id",2L);employee.setFullName("Employee");
        var own=folder(10,1,null,"Own");var foreign=folder(20,2,null,"Project");var child=folder(21,2,20L,"Reports");
        when(folders.findAll()).thenReturn(List.of(own,foreign,child));when(users.findAllById(any())).thenReturn(List.of(employee));
        var result=service.list("owner");assertEquals(3,result.size());
        assertTrue(result.stream().filter(f->f.id()==10L).findFirst().orElseThrow().canManage());
        var f=result.stream().filter(x->x.id()==20L).findFirst().orElseThrow();assertTrue(f.canManage());assertFalse(f.canOrganize());assertEquals("Employee",f.ownerName());
    }
    @Test void regularAndManagerCatalogsRemainScopedToOwnFolders() {
        var own=folder(10,1,null,"Own");when(folders.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of(own));
        for(String role:List.of("USER","MANAGER")) {users.findByUsernameIgnoreCase("owner").orElseThrow().setRole(role);assertEquals(1,service.list("owner").size());}
        verify(folders,never()).findAll();
    }
    @Test void adminRenamesAnotherOwnersFolderUsingThatOwnersLockAndSiblingNames() {
        users.findByUsernameIgnoreCase("owner").orElseThrow().setRole("ADMIN");
        User employee=new User();ReflectionTestUtils.setField(employee,"id",2L);
        when(users.findForStorageUpdate(2L)).thenReturn(Optional.of(employee));
        var foreign=folder(20,2,null,"Project");
        service.rename(20L,"New project","owner");assertEquals("New project",foreign.getName());
        verify(users).findForStorageUpdate(2L);verify(folders).findByOwnerIdOrderByNameAsc(2L);
    }
    @Test void adminDeletesForeignTreeAndTrashesItsFilesAsAdmin() {
        users.findByUsernameIgnoreCase("owner").orElseThrow().setRole("ADMIN");
        User employee=new User();ReflectionTestUtils.setField(employee,"id",2L);when(users.findForStorageUpdate(2L)).thenReturn(Optional.of(employee));
        var foreign=folder(20,2,null,"Project");when(folders.findByOwnerIdOrderByNameAsc(2L)).thenReturn(List.of(foreign));
        var file=document(5,2,20L,"ACTIVE");when(documents.findByOwnerId(2L)).thenReturn(List.of(file));
        service.delete(20L,"owner");verify(documentService).delete(5L,"owner");verify(folders).deleteAll(List.of(foreign));assertNull(file.getFolderId());
    }
}
