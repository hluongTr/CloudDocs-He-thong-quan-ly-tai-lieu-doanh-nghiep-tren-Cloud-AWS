package com.clouddocs.audit;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> getAll(Authentication authentication) {

        List<AuditLog> logs;

        String username = authentication.getName();

        // ADMIN xem toàn bộ hoạt động
        if (auditLogService.isAdmin(username)) {
            logs = auditLogService.getAll();
        }
        // USER chỉ xem hoạt động của chính mình
        else {
            logs = auditLogService.getForUser(username);
        }

        return logs.stream().map(AuditLogController::toResponse).toList();
    }

    @GetMapping("/recent")
    public List<AuditLogResponse> getRecent(Authentication authentication) {

        return auditLogService.getRecent(authentication.getName()).stream()
                .map(AuditLogController::toResponse)
                .toList();
    }

    private static AuditLogResponse toResponse(AuditLog log) {

        String username = log.getUser() != null ? log.getUser().getUsername() : null;

        Long documentId = log.getDocument() != null ? log.getDocument().getId() : null;

        String documentName = null;

        if (log.getDocument() != null) {
            documentName = log.getDocument().getTitle();
        }

        String targetUsername =
                log.getTargetUser() != null ? log.getTargetUser().getUsername() : null;

        return new AuditLogResponse(
                log.getId(),
                username,
                log.getUser() != null ? com.clouddocs.auth.ProfilePresentation.name(log.getUser()) : null,
                log.getAction(),
                documentId,
                documentName,
                targetUsername,
                log.getTargetUser() != null ? com.clouddocs.auth.ProfilePresentation.name(log.getTargetUser()) : null,
                displayDetails(log),
                log.getIpAddress(),
                log.getCreatedAt());
    }

    private static String displayDetails(AuditLog log) {
        String details = log.getDetails();
        if (details == null) return null;
        if (log.getUser() != null)
            details = replaceIdentity(details, log.getUser());
        if (log.getTargetUser() != null)
            details = replaceIdentity(details, log.getTargetUser());
        return details;
    }

    private static String replaceIdentity(String details, com.clouddocs.auth.User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) return details;
        String pattern = "(?<![\\p{L}\\p{N}_@.\\-])" + java.util.regex.Pattern.quote(user.getUsername())
                + "(?![\\p{L}\\p{N}_@.\\-])";
        return details.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(
                com.clouddocs.auth.ProfilePresentation.name(user)));
    }

    public record AuditLogResponse(
            Long id,
            String username,
            String fullName,
            String action,
            Long documentId,
            String documentName,
            String targetUsername,
            String targetFullName,
            String details,
            String ipAddress,
            java.time.Instant createdAt) {}
}
