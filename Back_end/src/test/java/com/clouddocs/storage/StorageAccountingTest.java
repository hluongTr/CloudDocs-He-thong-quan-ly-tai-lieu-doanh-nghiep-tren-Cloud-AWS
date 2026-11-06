package com.clouddocs.storage;
import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StorageAccountingTest {
    final DocumentRepository documents=mock(DocumentRepository.class);
    final StorageQuotaService service=new StorageQuotaService(documents,mock(UserRepository.class),1000,10000,100);
    User user() { User u=new User();ReflectionTestUtils.setField(u,"id",22L);return u; }
    Document file(long id,long owner,Long size,String type) {
        Document d=new Document();ReflectionTestUtils.setField(d,"id",id);d.setOwnerId(owner);d.setFileSize(size);d.setFileType(type);return d;
    }
    @Test void trashStillUsesQuotaButPurgedMetadataDoesNotCount() {
        Document trash=file(1,22,100L,"PDF");trash.setStatus("TRASHED");
        Document purged=file(2,22,0L,"PDF");purged.setStatus("PURGED");
        when(documents.findUploadConfirmedIds(List.of(1L))).thenReturn(List.of(1L));
        var usage=service.usageFor(user(),List.of(trash,purged));
        assertEquals(100,usage.usedBytes());assertEquals(1,usage.ownedDocuments());
        assertEquals(0,usage.zeroSizeCount());assertEquals(900,usage.remainingBytes());
    }
    @Test void chartQuotaAndConfirmationUseOneOwnedScope() {
        var owned=List.of(file(1,22,100L,"pdf"),file(2,22,200L,"ZIP"),file(3,99,999L,"PDF"));
        when(documents.findUploadConfirmedIds(List.of(1L,2L))).thenReturn(List.of(1L,1L));
        var usage=service.usageFor(user(),owned);
        assertEquals(300,usage.usedBytes());assertEquals(100,usage.confirmedBytes());assertEquals(200,usage.unconfirmedBytes());
        assertEquals(1,usage.unconfirmedDocuments());assertEquals(700,usage.remainingBytes());
        assertEquals(usage.usedBytes(),usage.storageByType().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void nullZeroAndNegativeSizesAreVisibleInsteadOfClaimingCompleteZeroUsage() {
        var usage=service.usageFor(user(),List.of(file(1,22,null,"PDF"),file(2,22,0L,"PDF"),file(3,22,-3L,"PDF")));
        assertEquals(2,usage.missingSizeCount());assertEquals(1,usage.zeroSizeCount());assertEquals(0,usage.usedBytes());
        assertEquals(3,usage.ownedDocuments());
    }
    @Test void removalReleasesOnlyRemovedMetadataAndEmptyAccountNeedsNoConfirmationQuery() {
        assertEquals(300,service.usageFor(user(),List.of(file(1,22,300L,"PDF"))).usedBytes());
        clearInvocations(documents);var empty=service.usageFor(user(),List.of());
        assertEquals(0,empty.usedBytes());assertEquals(1000,empty.remainingBytes());verifyNoInteractions(documents);
    }
}
