package com.clouddocs.storage;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageQuotaServiceTest {
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StorageQuotaService quotas =
            new StorageQuotaService(documents, users, 1000, 10000, 100);
    private User owner;

    @BeforeEach
    void setup() {
        owner = new User();
        ReflectionTestUtils.setField(owner, "id", 3L);
        when(users.findForStorageUpdate(3L)).thenReturn(Optional.of(owner));
    }

    @Test
    void reservesUpToTheExactUserLimit() {
        when(documents.sumStorageByOwnerId(3L)).thenReturn(900L);
        assertDoesNotThrow(() -> quotas.requireCapacity(3L, null, 100L));
        var order = inOrder(users, documents);
        order.verify(users).findForStorageUpdate(3L);
        order.verify(documents).sumStorageByOwnerId(3L);
    }

    @Test
    void rejectsOneByteOverQuota() {
        when(documents.sumStorageByOwnerId(3L)).thenReturn(901L);
        assertThrows(IllegalStateException.class, () -> quotas.requireCapacity(3L, null, 100L));
    }

    @Test
    void appliesAdminQuotaToOwner() {
        owner.setRole("ADMIN");
        when(documents.sumStorageByOwnerId(3L)).thenReturn(9900L);
        assertDoesNotThrow(() -> quotas.requireCapacity(3L, null, 100L));
        assertEquals(10000L, quotas.limitFor(owner));
    }

    @Test
    void replacementDoesNotCountTheOldFileTwice() {
        when(documents.sumStorageExcludingDocument(3L, 8L)).thenReturn(900L);
        assertDoesNotThrow(() -> quotas.requireCapacity(3L, 8L, 100L));
        verify(documents, never()).sumStorageByOwnerId(any());
    }

    @Test
    void rejectsNullNegativeAndOversizeFilesBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> quotas.requireCapacity(3L, null, null));
        assertThrows(IllegalArgumentException.class, () -> quotas.requireCapacity(3L, null, -1L));
        assertThrows(IllegalArgumentException.class, () -> quotas.requireCapacity(3L, null, 101L));
        verifyNoInteractions(users, documents);
    }

    @Test
    void clampsRemainingToZeroAfterRoleDowngrade() {
        when(documents.sumStorageByOwnerId(3L)).thenReturn(2000L);
        var file = new com.clouddocs.document.Document(); file.setOwnerId(3L); file.setFileSize(2000L);
        when(documents.findByOwnerId(3L)).thenReturn(java.util.List.of(file));
        var usage = quotas.usage(owner);
        assertEquals(2000L, usage.usedBytes());
        assertEquals(0L, usage.remainingBytes());
        assertThrows(IllegalStateException.class, () -> quotas.requireCapacity(3L, null, 0L));
    }

    @Test
    void usageOnlyQueriesOwnedDocuments() {
        var file = new com.clouddocs.document.Document(); file.setOwnerId(3L); file.setFileSize(250L);
        when(documents.findByOwnerId(3L)).thenReturn(java.util.List.of(file));
        assertEquals(750L, quotas.usage(owner).remainingBytes());
        verify(documents, never()).findAccessibleByUserId(any());
        verify(documents, never()).findAll();
    }

    @Test
    void defaultLimitsUseDecimalGbAndTbButKeepExistingMultipartFileLimit() {
        var defaults =
                new StorageQuotaService(
                        documents, users, 100_000_000_000L, 1_000_000_000_000L, 104_857_600L);
        assertEquals(100_000_000_000L, defaults.limitFor(owner));
        owner.setRole("ADMIN");
        assertEquals(1_000_000_000_000L, defaults.limitFor(owner));
        assertDoesNotThrow(() -> defaults.validateFileSize(104_857_600L));
        assertThrows(IllegalArgumentException.class, () -> defaults.validateFileSize(104_857_601L));
    }
}
