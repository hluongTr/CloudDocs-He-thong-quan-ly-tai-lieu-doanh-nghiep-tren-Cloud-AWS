package com.clouddocs.audit;

import com.clouddocs.auth.User;
import com.clouddocs.auth.UserRepository;
import com.clouddocs.common.ApiExceptionHandler;
import com.clouddocs.document.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditRecentAccessTest {
    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditLogService service =
            new AuditLogService(logs, users, mock(DocumentRepository.class));
    private final Authentication auth = mock(Authentication.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc =
                MockMvcBuilders.standaloneSetup(new AuditLogController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    private User account(long id, String name, String role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(name);
        user.setRole(role);
        when(users.findByUsernameIgnoreCase(name)).thenReturn(Optional.of(user));
        return user;
    }

    private AuditLog event(User actor, String details) {
        AuditLog log = new AuditLog();
        log.setUser(actor);
        log.setAction("DOWNLOAD");
        log.setDetails(details);
        return log;
    }

    @Test
    void regularUserGetsOnlyOwnRecentEventsAndCannotSelectAnotherAccount() throws Exception {
        User alice = account(3L, "alice", "USER");
        when(auth.getName()).thenReturn("alice");
        when(logs.findTop20ByUserIdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of(event(alice, "alice-only")));
        mvc.perform(
                        get("/api/audit-logs/recent")
                                .principal(auth)
                                .param("username", "admin")
                                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].details").value("alice-only"));
        verify(logs).findTop20ByUserIdOrderByCreatedAtDesc(3L);
        verify(logs, never()).findTop20ByOrderByCreatedAtDesc();
        verify(users, never()).findByUsernameIgnoreCase("admin");
    }

    @Test
    void anotherUserUsesTheirOwnIdRatherThanPreviousUsersId() throws Exception {
        User bob = account(4L, "bob", "USER");
        when(auth.getName()).thenReturn("bob");
        when(logs.findTop20ByUserIdOrderByCreatedAtDesc(4L))
                .thenReturn(List.of(event(bob, "bob-only")));
        mvc.perform(get("/api/audit-logs/recent").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bob"));
        verify(logs).findTop20ByUserIdOrderByCreatedAtDesc(4L);
        verify(logs, never()).findTop20ByUserIdOrderByCreatedAtDesc(3L);
        verify(logs, never()).findTop20ByOrderByCreatedAtDesc();
    }

    @Test
    void adminKeepsGlobalRecentEvents() throws Exception {
        account(1L, "admin", "ADMIN");
        User alice = account(3L, "alice", "USER");
        User bob = account(4L, "bob", "USER");
        when(auth.getName()).thenReturn("admin");
        when(logs.findTop20ByOrderByCreatedAtDesc())
                .thenReturn(List.of(event(alice, "a"), event(bob, "b")));
        mvc.perform(get("/api/audit-logs/recent").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].username").value("bob"));
        verify(logs, never()).findTop20ByUserIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void noPersonalEventsReturnsEmptyListWithoutGlobalFallback() throws Exception {
        account(3L, "alice", "USER");
        when(auth.getName()).thenReturn("alice");
        when(logs.findTop20ByUserIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
        mvc.perform(get("/api/audit-logs/recent").principal(auth))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(logs, never()).findTop20ByOrderByCreatedAtDesc();
    }

    @Test
    void unknownAccountDoesNotGetGlobalLogs() throws Exception {
        when(auth.getName()).thenReturn("missing");
        when(users.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/audit-logs/recent").principal(auth))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(logs);
    }
}
