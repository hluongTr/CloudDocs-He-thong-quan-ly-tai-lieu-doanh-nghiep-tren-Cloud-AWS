package com.clouddocs.auth;

import com.clouddocs.audit.AuditLogService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import com.clouddocs.common.ApiExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthProfileTest {
    @Test
    void displayNameCanBeSavedWithoutContactEmailOrDepartment() {
        user.setEmail(null); user.setDepartment(null); user.setRole("ADMIN");
        var result = service.updateDisplayName("alice", new AuthDtos.UpdateDisplayNameRequest(" Hán Quang Sang "));
        assertEquals("Hán Quang Sang", result.fullName());
        assertNull(result.email()); assertEquals("ADMIN", result.role()); assertEquals(7L, result.id());
        verify(users).saveAndFlush(user);
    }

    @Test
    void displayNameRejectsPlaceholdersAndBlockedAccounts() {
        assertThrows(IllegalArgumentException.class, () -> service.updateDisplayName("alice", new AuthDtos.UpdateDisplayNameRequest("Chưa cập nhật")));
        assertThrows(IllegalArgumentException.class, () -> service.updateDisplayName("alice", new AuthDtos.UpdateDisplayNameRequest("Alice\nAdmin")));
        user.setStatus("LOCKED");
        assertThrows(AccessDeniedException.class, () -> service.updateDisplayName("alice", new AuthDtos.UpdateDisplayNameRequest("Alice")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void displayNameEndpointCannotChangeIdentityOrRole() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(put("/api/auth/me/display-name")
                .principal(new UsernamePasswordAuthenticationToken("alice", null))
                .contentType("application/json")
                .content("{\"fullName\":\"Alice Nguyen\",\"id\":99,\"role\":\"ADMIN\",\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("fullName").value("Alice Nguyen"))
                .andExpect(jsonPath("id").value(7)).andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("status").value("ACTIVE"));
        mvc.perform(put("/api/auth/me/display-name")
                .principal(new UsernamePasswordAuthenticationToken("alice", null))
                .contentType("application/json").content("{\"fullName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
    private final UserRepository users = mock(UserRepository.class);

    private final AuthService service =
            new AuthService(users, mock(AuditLogService.class));
    private User user;

    @BeforeEach
    void setup() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("hashed-password");
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
    }

    @Test
    void updatesOnlyEditableFieldsAndNormalizesEmail() {
        var result =
                service.updateProfile(
                        "alice", request(" Alice Nguyen ", "NEW@EXAMPLE.COM", " IT "));
        assertEquals("Alice Nguyen", result.fullName());
        assertEquals("new@example.com", result.email());
        assertEquals("IT", result.department());
        assertEquals("alice", result.username());
        assertEquals("USER", result.role());
        assertEquals("ACTIVE", result.status());
        assertEquals("hashed-password", user.getPassword());
        verify(users).saveAndFlush(user);
    }

    @Test
    void acceptsOwnEmail() {
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        assertDoesNotThrow(
                () -> service.updateProfile("alice", request("Alice", "alice@example.com", "IT")));
    }

    @Test
    void rejectsAnotherUsersEmailBeforeMutatingProfile() {
        User other = new User();
        ReflectionTestUtils.setField(other, "id", 8L);
        when(users.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.of(other));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateProfile("alice", request("Alice", "other@example.com", "IT")));
        assertEquals("alice@example.com", user.getEmail());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void allowsAdminButRejectsLockedAccounts() {
        user.setRole("ADMIN");
        assertDoesNotThrow(
                () ->
                        service.updateProfile(
                                "alice", request("Alice Admin", "alice@example.com", "IT")));
        verify(users).saveAndFlush(user);
        reset(users);
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        user.setStatus("LOCKED");
        assertThrows(
                AccessDeniedException.class,
                () -> service.updateProfile("alice", request("Alice", "alice@example.com", "IT")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void handlesConcurrentDuplicateEmail() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(users).saveAndFlush(user);
        assertThrows(
                IllegalStateException.class,
                () -> service.updateProfile("alice", request("Alice", "alice@example.com", "IT")));
    }

    @Test
    void validatesBlankInvalidAndOversizedFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals(3, validator.validate(request(" ", "invalid", " ")).size());
            assertEquals(
                    2,
                    validator
                            .validate(
                                    request("a".repeat(121), "alice@example.com", "a".repeat(101)))
                            .size());
            assertTrue(validator.validate(request("Alice", "alice@example.com", "IT")).isEmpty());
        }
    }

    @Test
    void endpointUsesAuthenticatedIdentityAndIgnoresPrivilegedFields() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new AuthController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(
                        put("/api/auth/me")
                                .principal(new UsernamePasswordAuthenticationToken("alice", null))
                                .contentType("application/json")
                                .content(
                                        """
                        {"fullName":"Alice","email":"alice@example.com","department":"IT",
                         "id":8,"username":"other","role":"ADMIN","status":"LOCKED"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(7))
                .andExpect(jsonPath("username").value("alice"))
                .andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("password").doesNotExist());
    }

    @Test
    void endpointReturnsValidationAndForbiddenResponses() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new AuthController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(
                        put("/api/auth/me")
                                .principal(new UsernamePasswordAuthenticationToken("alice", null))
                                .contentType("application/json")
                                .content(
                                        """
                        {"fullName":" ","email":"invalid","department":"IT"}
                        """))
                .andExpect(status().isBadRequest());
        user.setStatus("LOCKED");
        mvc.perform(
                        put("/api/auth/me")
                                .principal(new UsernamePasswordAuthenticationToken("alice", null))
                                .contentType("application/json")
                                .content(
                                        """
                        {"fullName":"Alice","email":"alice@example.com","department":"IT"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("error").value("FORBIDDEN"));
        verify(users, never()).saveAndFlush(any());
    }

    private AuthDtos.UpdateProfileRequest request(String name, String email, String department) {
        return new AuthDtos.UpdateProfileRequest(name, email, department);
    }
}
