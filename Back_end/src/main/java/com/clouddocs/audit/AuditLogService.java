package com.clouddocs.audit;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.document.Document;
import com.clouddocs.document.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            DocumentRepository documentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(
            String username, String action, Long documentId, Long targetUserId, String details) {

        return logInTransaction(username, action, documentId, targetUserId, details);
    }

    @Transactional
    public AuditLog logInTransaction(
            String username, String action, Long documentId, Long targetUserId, String details) {
        AuditLog log = new AuditLog();

        User user = null;

        if (username != null && !username.isBlank()) {
            user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        }

        log.setUser(user);
        log.setAction(action);

        if (documentId != null) {
            Document document = documentRepository.findById(documentId).orElse(null);

            log.setDocument(document);
        }

        if (targetUserId != null) {
            User targetUser = userRepository.findById(targetUserId).orElse(null);

            log.setTargetUser(targetUser);
        }

        log.setDetails(details == null || details.length() <= 500 ? details : details.substring(0, 500));

        return auditLogRepository.save(log);
    }

    public static List<AuditLog> visibleLogs(List<AuditLog> logs){
        java.util.Set<String> uploaded=logs.stream().filter(l->"UPLOAD".equals(l.getAction())&&l.getDocument()!=null&&l.getUser()!=null).map(l->l.getDocument().getId()+":"+l.getUser().getId()).collect(java.util.stream.Collectors.toSet());
        return logs.stream().filter(l->!"CREATE_DOCUMENT".equals(l.getAction())||l.getDocument()==null||l.getUser()==null||!uploaded.contains(l.getDocument().getId()+":"+l.getUser().getId())).toList();
    }
    @Transactional(readOnly = true)
    public List<AuditLog> getAll() {
        return visibleLogs(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecent(String username) {
        User user =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return visibleLogs(auditLogRepository.findTop20ByOrderByCreatedAtDesc());
        }
        // Filter by the authenticated actor before applying the top-20 limit.
        return visibleLogs(auditLogRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getForUser(String username) {

        User user =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));

        return visibleLogs(auditLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(String username) {

        User user =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));

        return "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
