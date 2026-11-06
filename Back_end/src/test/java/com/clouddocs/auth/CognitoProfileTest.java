package com.clouddocs.auth;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CognitoProfileTest {
    static final String ISSUER="https://cognito-idp.us-east-1.amazonaws.com/pool";
    static final String SUB="44b8d4b8-c031-7034-48e6-03361d1d87b6";
    final UserRepository users=mock(UserRepository.class);
    final CognitoIdentityRepository identities=mock(CognitoIdentityRepository.class);
    final CognitoProfileClient profiles=mock(CognitoProfileClient.class);
    final CognitoUsers service=new CognitoUsers(identities,users,"ACTIVE",profiles);
    User user;
    Jwt jwt() { return Jwt.withTokenValue("validated-access-token").header("alg","RS256").issuer(ISSUER).subject(SUB)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(); }
    @BeforeEach void setup() {
        user=new User();ReflectionTestUtils.setField(user,"id",22L);user.setUsername("c_internal");
        user.setFullName("Người dùng CloudDocs");user.setEmail("old@identity.invalid");user.setRole("ADMIN");
        when(identities.findByIssuerAndSubject(ISSUER,SUB)).thenReturn(Optional.of(new CognitoIdentity(ISSUER,SUB,user)));
    }
    @Test void repairsOnlyMissingFieldsFromMatchingVerifiedUserInfo() {
        when(profiles.profile(any())).thenReturn(Map.of("sub",SUB,"name","Hán Quang Sang","email","SANG@EXAMPLE.COM","email_verified","true"));
        var auth=service.authenticate(jwt());
        assertEquals("Hán Quang Sang",user.getFullName());assertEquals("sang@example.com",user.getEmail());
        assertEquals(22L,user.getId());assertEquals("ADMIN",user.getRole());assertEquals("ACTIVE",user.getStatus());
        assertEquals("c_internal",auth.getName());assertTrue(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")));
    }
    @Test void neverOverwritesCustomNameOrContactEmail() {
        user.setFullName("Tên đã chỉnh sửa");user.setEmail("contact@example.com");
        service.authenticate(jwt());verifyNoInteractions(profiles);verify(users,never()).saveAndFlush(any());
    }
    @Test void rejectsDifferentSubjectAndUnverifiedEmail() {
        when(profiles.profile(any())).thenReturn(Map.of("sub","other","name","Attacker","email","fake@example.com","email_verified",true));
        service.authenticate(jwt());assertEquals("Người dùng CloudDocs",user.getFullName());assertEquals("old@identity.invalid",user.getEmail());
        when(profiles.profile(any())).thenReturn(Map.of("sub",SUB,"given_name","Sang","family_name","Nguyen","email","unverified@example.com","email_verified",false));
        service.authenticate(jwt());assertEquals("Sang Nguyen",user.getFullName());assertEquals("old@identity.invalid",user.getEmail());
    }
    @Test void matchingEmailNeverLinksAnotherAccount() {
        User other=new User();ReflectionTestUtils.setField(other,"id",99L);
        when(users.findByEmailIgnoreCase("exists@example.com")).thenReturn(Optional.of(other));
        when(profiles.profile(any())).thenReturn(Map.of("sub",SUB,"email","exists@example.com","email_verified",true));
        service.authenticate(jwt());assertEquals(22L,user.getId());assertEquals("old@identity.invalid",user.getEmail());
        verify(identities,never()).saveAndFlush(any());
    }
    @Test void noClaimsMeansNoInventedEmailAndNoAutomaticAdmin() {
        when(identities.findByIssuerAndSubject(ISSUER,SUB)).thenReturn(Optional.empty());
        service.authenticate(jwt());var captured=org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captured.capture());assertNull(captured.getValue().getEmail());
        assertEquals("USER",captured.getValue().getRole());assertEquals("Chưa cập nhật",ProfilePresentation.name(captured.getValue()));
    }
    @Test void placeholdersAreNotReturnedAsContactInformation() {
        assertNull(ProfilePresentation.email(user));assertEquals("Chưa cập nhật",ProfilePresentation.name(user));
        when(users.findByUsernameIgnoreCase("c_internal")).thenReturn(Optional.of(user));
        var response=new AuthService(users,mock(com.clouddocs.audit.AuditLogService.class)).me("c_internal");
        assertNull(response.email());assertEquals("Chưa cập nhật",response.fullName());assertEquals("ADMIN",response.role());
    }
    @Test void lockedProfileDoesNotTriggerProfileSyncOrGrantAuthorities() {
        user.setStatus("LOCKED");assertTrue(service.authenticate(jwt()).getAuthorities().isEmpty());verifyNoInteractions(profiles);
    }
}
