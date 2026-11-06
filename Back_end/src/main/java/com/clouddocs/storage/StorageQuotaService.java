package com.clouddocs.storage;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.clouddocs.document.Document;
import java.util.*;

@Service
public class StorageQuotaService {
    private final DocumentRepository documents;
    private final UserRepository users;
    private final long userLimit;
    private final long adminLimit;
    private final long maxFileBytes;

    public StorageQuotaService(
            DocumentRepository documents,
            UserRepository users,
            @Value("${storage.quota.user-bytes:100000000000}") long userLimit,
            @Value("${storage.quota.admin-bytes:1000000000000}") long adminLimit,
            @Value("${storage.quota.max-file-bytes:104857600}") long maxFileBytes) {
        if (userLimit <= 0 || adminLimit <= 0 || maxFileBytes <= 0) {
            throw new IllegalArgumentException("Hạn mức dung lượng phải lớn hơn 0.");
        }
        this.documents = documents;
        this.users = users;
        this.userLimit = userLimit;
        this.adminLimit = adminLimit;
        this.maxFileBytes = maxFileBytes;
    }

    public long limitFor(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) ? adminLimit : userLimit;
    }

    public void validateFileSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            throw new IllegalArgumentException("Cần cung cấp kích thước file hợp lệ.");
        }
        if (bytes > maxFileBytes) {
            throw new IllegalArgumentException(
                    "File vượt giới hạn " + maxFileBytes + " byte (mặc định 100 MB).");
        }
    }

    // Serialize allocations for the owner, including uploads by an editor/admin.
    // The lock remains held by the calling document transaction until commit.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireCapacity(Long ownerId, Long documentId, Long bytes) {
        validateFileSize(bytes);
        User owner =
                users.findForStorageUpdate(ownerId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Không tìm thấy chủ sở hữu tài liệu."));
        long otherBytes =
                documentId == null
                        ? documents.sumStorageByOwnerId(ownerId)
                        : documents.sumStorageExcludingDocument(ownerId, documentId);
        long limit = limitFor(owner);
        if (otherBytes > limit || bytes > limit - otherBytes) {
            throw new IllegalStateException(
                    "Chủ sở hữu đã hết dung lượng. Hãy xóa tài liệu không cần thiết trước khi tải lên.");
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(User user) {
        return usageFor(user, documents.findByOwnerId(user.getId()));
    }

    public Usage usageFor(User user, List<Document> owned) {
        List<Document> scope = owned.stream().filter(d -> !d.isPurged()).filter(d -> Objects.equals(d.getOwnerId(), user.getId())).toList();
        List<Long> ids = scope.stream().map(Document::getId).filter(Objects::nonNull).toList();
        Set<Long> confirmed = ids.isEmpty() ? Set.of() : new HashSet<>(documents.findUploadConfirmedIds(ids));
        long used = 0, confirmedBytes = 0;
        int missing = 0, zero = 0, unconfirmed = 0;
        Map<String, Long> byType = new TreeMap<>();
        Map<String, Long> confirmedByType = new TreeMap<>();
        for (Document d : scope) {
            boolean uploaded = d.getId() != null && confirmed.contains(d.getId());
            if (!uploaded) unconfirmed++;
            Long bytes = d.getFileSize();
            if (bytes == null || bytes < 0) { missing++; continue; }
            if (bytes == 0) zero++;
            used = Math.addExact(used, bytes);
            if (uploaded) confirmedBytes = Math.addExact(confirmedBytes, bytes);
            String type = d.getFileType() == null || d.getFileType().isBlank() ? "OTHER" : d.getFileType().toUpperCase(Locale.ROOT);
            byType.merge(type, bytes, Math::addExact);
            if (uploaded) confirmedByType.merge(type, bytes, Math::addExact);
        }
        long limit = limitFor(user);
        return new Usage(used, limit, Math.max(0, limit - used), maxFileBytes,
                Map.copyOf(byType), scope.size(), missing, zero, confirmedBytes, used - confirmedBytes, unconfirmed, Map.copyOf(confirmedByType));
    }

    // Quota charges declared metadata, including unconfirmed allocations; never bucket capacity.
    public record Usage(long usedBytes, long limitBytes, long remainingBytes, long maxFileBytes,
            Map<String, Long> storageByType, int ownedDocuments, int missingSizeCount, int zeroSizeCount,
            long confirmedBytes, long unconfirmedBytes, int unconfirmedDocuments, Map<String, Long> confirmedStorageByType) {
        public Usage(long used, long limit, long remaining, long maxFile) {
            this(used, limit, remaining, maxFile, Map.of(), 0, 0, 0, used, 0, 0, Map.of());
        }
    }
}
