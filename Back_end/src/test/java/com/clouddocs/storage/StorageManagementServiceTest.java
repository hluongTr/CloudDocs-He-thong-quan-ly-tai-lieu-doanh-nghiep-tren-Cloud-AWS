package com.clouddocs.storage;
import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StorageManagementServiceTest {
 final UserRepository users=mock(UserRepository.class);final DocumentRepository docs=mock(DocumentRepository.class);
 final StorageQuotaService quotas=new StorageQuotaService(docs,users,1000,10000,100);
 final StorageManagementService service=new StorageManagementService(users,docs,quotas);
 User admin,user;Document small,large,trash,purged,unknown;
 User account(long id,String role){User u=new User();ReflectionTestUtils.setField(u,"id",id);u.setUsername("u"+id);u.setFullName("Name "+id);u.setRole(role);u.setStatus("ACTIVE");when(users.findByUsernameIgnoreCase(u.getUsername())).thenReturn(Optional.of(u));return u;}
 Document file(long id,long owner,Long size,String status){Document d=new Document();ReflectionTestUtils.setField(d,"id",id);d.setTitle("File "+id);d.setFileName("file.txt");d.setFileType("TXT");d.setOwnerId(owner);d.setStatus(status);d.setFileSize(size);return d;}
 @BeforeEach void setup(){admin=account(1,"ADMIN");user=account(2,"USER");small=file(1,1,12L,"ACTIVE");large=file(2,2,11000L,"ACTIVE");trash=file(3,2,100L,"TRASHED");purged=file(4,2,9000L,"PURGED");unknown=file(5,2,null,"ACTIVE");
 when(users.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(admin,user));when(users.findAllById(any())).thenReturn(List.of(admin,user));when(docs.findAll()).thenReturn(List.of(small,large,trash,purged,unknown));when(docs.findByOwnerId(2L)).thenReturn(List.of(large,trash,purged,unknown));when(docs.findUploadConfirmedIds(any())).thenReturn(List.of(1L,2L));}
 @Test void adminSeesSystemTotalsAndAllFilesSortedLargestFirst(){var r=service.management("u1");assertEquals("SYSTEM",r.scope());assertEquals(11112,r.usage().usedBytes());assertEquals(10000,r.usage().limitBytes());assertEquals(0,r.usage().remainingBytes());assertEquals(100,r.trashBytes());assertEquals(List.of(2L,3L,1L,5L),r.files().stream().map(StorageManagementService.FileItem::id).toList());assertEquals("Name 2",r.files().getFirst().owner());assertEquals(1,r.usage().missingSizeCount());assertEquals(2,r.usage().unconfirmedDocuments());assertEquals(100,r.usage().unconfirmedBytes());assertEquals(11112,r.usage().storageByType().get("TXT"));assertEquals("Name 2",r.accounts().getFirst().displayName());}
 @Test void regularAccountCannotSeeOtherOwnersOrAccountBreakdown(){var r=service.management("u2");assertEquals("PERSONAL",r.scope());assertTrue(r.accounts().isEmpty());assertEquals(11100,r.usage().usedBytes());assertEquals(1000,r.usage().limitBytes());assertFalse(r.files().stream().anyMatch(f->f.id()==1));verify(docs,never()).findAll();verify(users,never()).findAllByOrderByCreatedAtDesc();}
 @Test void sidebarSummaryUsesSameSystemScope(){var usage=service.summary("u1");assertEquals(11112,usage.usedBytes());assertEquals(10000,usage.limitBytes());}
 @Test void deletedAccountsDoNotAddQuotaButRetainedFilesStillCount(){user.setStatus("DELETED");var r=service.management("u1");assertEquals(10000,r.usage().limitBytes());assertEquals(11112,r.usage().usedBytes());assertEquals(1,r.accounts().size());}
 @Test void lockedAccountIsRejected(){user.setStatus("LOCKED");assertThrows(AccessDeniedException.class,()->service.management("u2"));assertThrows(AccessDeniedException.class,()->service.summary("u2"));verifyNoInteractions(docs);}
 @Test void emptySystemHasZeroUsage(){when(docs.findAll()).thenReturn(List.of());var r=service.management("u1");assertEquals(0,r.usage().usedBytes());assertEquals(10000,r.usage().remainingBytes());assertTrue(r.files().isEmpty());}
}
