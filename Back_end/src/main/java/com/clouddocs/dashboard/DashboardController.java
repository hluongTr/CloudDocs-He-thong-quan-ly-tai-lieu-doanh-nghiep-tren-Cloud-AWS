package com.clouddocs.dashboard;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.Document;
import com.clouddocs.document.DocumentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.clouddocs.audit.AuditLogRepository;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class DashboardController {

    private final AuditLogRepository auditLogRepository;
    private final DocumentRepository repository;
    private final UserRepository userRepository;
    private final com.clouddocs.storage.StorageQuotaService quotas;

    public DashboardController(
            DocumentRepository repository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            com.clouddocs.storage.StorageQuotaService quotas) {

        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.quotas = quotas;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/api/dashboard/summary")
    public Map<String, Object> summary(Authentication authentication) {
        User user = userRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();

        List<Document> documents =
                "ADMIN".equalsIgnoreCase(user.getRole())
                        ? repository.findAll()
                        : repository.findAccessibleByUserId(user.getId());

        List<Document> storedDocuments = documents.stream().filter(d -> !d.isPurged()).toList();
        documents = documents.stream().filter(Document::isAvailable).toList();

        Instant monthStart =
                ZonedDateTime.now(ZoneId.systemDefault())
                        .withDayOfMonth(1)
                        .toLocalDate()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant();

        long uploadedThisMonth =
                documents.stream()
                        .filter(
                                d ->
                                        d.getCreatedAt() != null
                                                && !d.getCreatedAt().isBefore(monthStart))
                        .count();

        List<Document> ownedDocuments =
                "ADMIN".equalsIgnoreCase(user.getRole())
                        ? storedDocuments.stream()
                                .filter(d -> Objects.equals(d.getOwnerId(), user.getId()))
                                .toList()
                        : repository.findByOwnerId(user.getId());

        var usage = quotas.usageFor(user, ownedDocuments);
        long totalBytes = usage.confirmedBytes();
        Map<String, Long> storageByType = usage.confirmedStorageByType();

        var ownerNames = com.clouddocs.document.DocumentOwnerNames.resolve(documents, userRepository);
        List<Map<String, Object>> recent =
                documents.stream()
                        .sorted(
                                Comparator.comparing(
                                        Document::getUpdatedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5)
                        .map(
                                d -> {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("id", d.getId());
                                    item.put("title", d.getTitle());
                                    item.put("fileType", d.getFileType());
                                    item.put("fileSize", d.getFileSize());
                                    item.put("owner", com.clouddocs.document.DocumentOwnerNames.name(d, ownerNames));
                                    item.put("department", d.getDepartment());
                                    item.put("updatedAt", d.getUpdatedAt());
                                    item.put("status", d.getStatus());
                                    return item;
                                })
                        .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDocuments", documents.size());
        result.put("uploadedThisMonth", uploadedThisMonth);
        long downloads =
                "ADMIN".equalsIgnoreCase(user.getRole())
                        ? auditLogRepository.countByAction("DOWNLOAD")
                        : auditLogRepository.countByActionAndUserId("DOWNLOAD", user.getId());

        result.put("downloads", downloads);
        result.put("downloadsAvailable", true);
        result.put("storageBytes", totalBytes);
        result.put("storageQuota", usage);
        result.put("storageByType", storageByType);
        result.put("storageDistribution", storageDistribution(documents, user));
        result.put("recentDocuments", recent);
        result.put("isAdmin", "ADMIN".equalsIgnoreCase(user.getRole()));
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            Map<Long, Long> usageByOwner =
                    storedDocuments.stream()
                            .filter(d -> d.getOwnerId() != null && d.getFileSize() != null && d.getFileSize() >= 0)
                            .collect(
                                    Collectors.groupingBy(
                                            Document::getOwnerId,
                                            Collectors.summingLong(Document::getFileSize)));
            result.put(
                    "systemStorageBytes",
                    storedDocuments.stream()
                            .map(Document::getFileSize)
                            .filter(size -> size != null && size >= 0)
                            .mapToLong(Long::longValue)
                            .sum());
            result.put(
                    "storageUsers",
                    userRepository.findAllByOrderByCreatedAtDesc().stream()
                            .filter(account -> !"DELETED".equalsIgnoreCase(account.getStatus()))
                            .map(
                                    account -> {
                                        long used = usageByOwner.getOrDefault(account.getId(), 0L);
                                        long limit = quotas.limitFor(account);
                                        return Map.of(
                                                "username",
                                                account.getUsername(),
                                                "displayName",
                                                com.clouddocs.auth.ProfilePresentation.name(account),
                                                "userId",
                                                account.getId(),
                                                "role",
                                                account.getRole(),
                                                "usedBytes",
                                                used,
                                                "limitBytes",
                                                limit,
                                                "remainingBytes",
                                                Math.max(0, limit - used));
                                    })
                            .toList());
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            result.put("systemMissingSizeCount", storedDocuments.stream().filter(d -> d.getFileSize() == null || d.getFileSize() < 0).count());
            result.put("unlinkedOwnerCount", storedDocuments.stream().filter(d -> d.getOwnerId() == null).count());
        }
        return result;
    }

    // Distribution matches active document metadata; personal quota independently includes trash.
    private Map<String, Object> storageDistribution(List<Document> documents, User user) {
        boolean admin = "ADMIN".equalsIgnoreCase(user.getRole());
        List<Document> scope = documents.stream()
                .filter(d -> admin || Objects.equals(d.getOwnerId(), user.getId())).toList();
        Map<String, Long> byType = new TreeMap<>();
        long used = 0;
        int missing = 0, zero = 0;
        for (Document document : scope) {
            Long bytes = document.getFileSize();
            if (bytes == null || bytes < 0) { missing++; continue; }
            if (bytes == 0) zero++;
            String type = document.getFileType() == null || document.getFileType().isBlank()
                    ? "OTHER" : document.getFileType().trim().toUpperCase(Locale.ROOT);
            byType.merge(type, bytes, Math::addExact);
            used = Math.addExact(used, bytes);
        }
        return Map.of("scope", admin ? "SYSTEM" : "PERSONAL", "usedBytes", used,
                "storageByType", byType, "documentCount", scope.size(),
                "missingSizeCount", missing, "zeroSizeCount", zero);
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "clouddocs-backend");
    }
}
