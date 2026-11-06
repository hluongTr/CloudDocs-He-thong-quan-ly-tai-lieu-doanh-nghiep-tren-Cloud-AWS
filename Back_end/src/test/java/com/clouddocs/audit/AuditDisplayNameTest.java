package com.clouddocs.audit;
import com.clouddocs.auth.User;
import com.clouddocs.document.Document;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AuditDisplayNameTest {
    @Test void existingLogsResolveOpaqueCognitoIdsToProfileNames() {
        AuditLogService service = mock(AuditLogService.class);
        User actor = new User(); actor.setUsername("opaque-cognito-sub"); actor.setFullName("Trần Hải Lương");
        User target = new User(); target.setUsername("other-opaque-id"); target.setFullName("Người nhận");
        Document doc = new Document(); doc.setTitle("Báo cáo");
        AuditLog log = new AuditLog(); log.setUser(actor); log.setTargetUser(target); log.setDocument(doc); log.setAction("SHARE");
        log.setDetails("opaque-cognito-sub chia sẻ cho other-opaque-id");
        when(service.isAdmin("admin")).thenReturn(true); when(service.getAll()).thenReturn(List.of(log));
        var response = new AuditLogController(service).getAll(new UsernamePasswordAuthenticationToken("admin", "unused")).getFirst();
        assertEquals("Trần Hải Lương", response.fullName()); assertEquals("Người nhận", response.targetFullName());
        assertFalse(response.details().contains("opaque")); assertEquals("Báo cáo", response.documentName());
    }
}
