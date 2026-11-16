package com.clouddocs.document;

import com.clouddocs.auth.*;
import com.clouddocs.folder.FolderService;
import com.clouddocs.storage.S3StorageService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadServiceTest {
 final DocumentService documents=mock(DocumentService.class);
 final DocumentRepository repository=mock(DocumentRepository.class);
 final UserRepository users=mock(UserRepository.class);
 final S3StorageService storage=mock(S3StorageService.class);
 final FolderService folders=mock(FolderService.class);
 final DocumentUploadService service=new DocumentUploadService(documents,repository,users,storage,folders,"bucket");
 final UUID requestId=UUID.randomUUID();
 final MockMultipartFile file=new MockMultipartFile("file","report.pdf","application/pdf",new byte[]{1,2,3});
 final DocumentDtos.CreateDocumentRequest metadata=new DocumentDtos.CreateDocumentRequest("report.pdf","Note","report.pdf","PDF",999L,null);
 final DocumentDtos.DocumentResponse response=new DocumentDtos.DocumentResponse(9L,"report.pdf","Note","report.pdf","PDF",3L,"Owner","IT","ACTIVE","2026-11-15T00:00:00Z","OWNER");
 Document document;
 @BeforeEach void setup(){User u=new User();ReflectionTestUtils.setField(u,"id",1L);when(users.findByUsernameIgnoreCase("owner")).thenReturn(Optional.of(u));when(users.findForStorageUpdate(1L)).thenReturn(Optional.of(u));when(documents.create(any(),eq("owner"))).thenReturn(response);document=new Document();ReflectionTestUtils.setField(document,"id",9L);document.setOwnerId(1L);document.setFileName("report.pdf");document.setFileSize(3L);when(repository.findById(9L)).thenReturn(Optional.of(document));when(documents.findById(9L,"owner")).thenReturn(response);}
 @AfterEach void cleanup(){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();}
 @Test void successUploadsAndConfirmsWithinSameTransactionAndUsesActualBytes(){TransactionSynchronizationManager.initSynchronization();assertEquals(response,service.upload(file,metadata,requestId,5L,"owner"));var order=inOrder(documents,storage,folders);order.verify(documents).create(argThat(r->r.fileSize()==3L),eq("owner"));order.verify(storage).uploadReplacement(eq("bucket"),anyString(),any(),eq(3L));order.verify(documents).completeS3Upload(9L,"owner");order.verify(folders).move(List.of(9L),5L,"owner");TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));verify(storage,never()).deleteIfExists(any(),any());}
 @Test void failedS3UploadNeverConfirmsOrMovesAndRollbackCleansAttemptObject(){TransactionSynchronizationManager.initSynchronization();doThrow(new IllegalStateException("S3 denied")).when(storage).uploadReplacement(any(),any(),any(),anyLong());assertThrows(IllegalStateException.class,()->service.upload(file,metadata,requestId,null,"owner"));verify(documents,never()).completeS3Upload(any(),any());verifyNoInteractions(folders);TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));verify(storage).deleteIfExists("bucket",document.getS3Key());}
 @Test void failedFolderPlacementRollsBackAndCleansS3Object(){TransactionSynchronizationManager.initSynchronization();doThrow(new IllegalArgumentException("Folder gone")).when(folders).move(any(),any(),any());assertThrows(IllegalArgumentException.class,()->service.upload(file,metadata,requestId,5L,"owner"));TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));verify(storage).deleteIfExists("bucket",document.getS3Key());}
 @Test void lostResponseRetryReturnsPreviouslyCommittedDocumentWithoutReupload(){when(repository.findFirstByOwnerIdAndS3KeyStartingWith(eq(1L),anyString())).thenReturn(Optional.of(document));assertEquals(response,service.upload(file,metadata,requestId,null,"owner"));verify(documents,never()).create(any(),any());verifyNoInteractions(storage,folders);}
 @Test void retryCannotResurrectDeletedDocumentOrUseReceiptForDifferentFile(){when(repository.findFirstByOwnerIdAndS3KeyStartingWith(eq(1L),anyString())).thenReturn(Optional.of(document));document.setStatus("TRASHED");assertThrows(IllegalStateException.class,()->service.upload(file,metadata,requestId,null,"owner"));document.setStatus("ACTIVE");document.setFileName("different.pdf");assertThrows(IllegalStateException.class,()->service.upload(file,metadata,requestId,null,"owner"));verifyNoInteractions(storage);}
 @Test void successiveFailedAttemptsHaveDifferentObjectKeysForSafeCleanup(){doThrow(new IllegalStateException("offline")).when(storage).uploadReplacement(any(),any(),any(),anyLong());assertThrows(IllegalStateException.class,()->service.upload(file,metadata,requestId,null,"owner"));String first=document.getS3Key();assertThrows(IllegalStateException.class,()->service.upload(file,metadata,requestId,null,"owner"));assertNotEquals(first,document.getS3Key());assertTrue(document.getS3Key().startsWith("documents/uploads/1/"+requestId+"/"));}
}
