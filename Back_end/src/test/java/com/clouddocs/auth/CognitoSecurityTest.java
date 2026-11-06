package com.clouddocs.auth;

import com.clouddocs.config.SecurityConfig;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CognitoSecurityTest {
    static final String ISSUER = "https://cognito-idp.ap-southeast-1.amazonaws.com/ap-southeast-1_test";
    static final String SUB = "12345678-1234-1234-1234-123456789abc";
    static KeyPair keys;
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    User user;
    UserRepository users;
    CognitoIdentityRepository identities;
    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); keys = generator.generateKeyPair();
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CognitoUsers.class, Endpoints.class})
    static class Config {
        @Bean CognitoProfileClient profiles() { return mock(CognitoProfileClient.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean CognitoIdentityRepository identities() { return mock(CognitoIdentityRepository.class); }
        @Bean @Primary JwtDecoder testDecoder() {
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER), SecurityConfig.accessValidator("client")));
            return decoder;
        }
    }
    @RestController static class Endpoints {
        @GetMapping({"/api/auth/me", "/api/users/test", "/api/documents/test"})
        String ok() { return "ok"; }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "auth.cognito.issuer=" + ISSUER, "auth.cognito.client-id=client");
        context.register(Config.class); context.refresh();
        users = context.getBean(UserRepository.class); identities = context.getBean(CognitoIdentityRepository.class);
        user = new User(); user.setUsername("internal"); user.setRole("USER"); user.setStatus("ACTIVE");
        when(identities.findByIssuerAndSubject(ISSUER,SUB)).thenReturn(Optional.of(new CognitoIdentity(ISSUER,SUB,user)));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { context.close(); }
    String token(String issuer, String client, String use, String sub, Instant expires) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(sub).issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(expires)).claim("client_id",client).claim("token_use",use)
                .claim("cognito:groups",List.of("ADMIN")).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey)keys.getPrivate())); return jwt.serialize();
    }
    String valid() throws Exception { return token(ISSUER,"client","access",SUB,Instant.now().plusSeconds(600)); }
    @Test void validatesTokensAndRejectsLegacyAndInvalidClaims() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        for(String bad : List.of("fake", token("https://wrong.example","client","access",SUB,Instant.now().plusSeconds(600)),
                token(ISSUER,"other","access",SUB,Instant.now().plusSeconds(600)),
                token(ISSUER,"client","id",SUB,Instant.now().plusSeconds(600)),
                token(ISSUER,"client","access","bad",Instant.now().plusSeconds(600)),
                token(ISSUER,"client","access",SUB,Instant.now().minusSeconds(300))))
            mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+bad)).andExpect(status().isUnauthorized());
        var legacy = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder().subject("internal").build());
        legacy.sign(new MACSigner(new byte[32]));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+legacy.serialize())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+valid())).andExpect(status().isOk());
    }
    @Test void databaseRoleAndStatusTakeEffectOnNextRequest() throws Exception {
        String token=valid();
        mvc.perform(get("/api/users/test").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        user.setRole("ADMIN");
        mvc.perform(get("/api/users/test").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        user.setRole("USER");
        mvc.perform(get("/api/users/test").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        for(String status : List.of("LOCKED","PENDING_APPROVAL","DELETED")) {
            user.setStatus(status);
            mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/documents/test").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        }
        verify(users,never()).saveAndFlush(any());
    }
    @Test void removedEndpointsHaveNoLocalAuthenticationFunction() throws Exception {
        for(String path : List.of("login","register","change-password"))
            mvc.perform(post("/api/auth/"+path).header("Authorization","Bearer "+valid())).andExpect(status().isForbidden());
    }
    @Test void provisioningFailuresAreNotTokenErrors() throws Exception {
        when(identities.findByIssuerAndSubject(ISSUER,SUB)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+valid())).andExpect(status().isConflict());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"))
                .when(identities).findByIssuerAndSubject(ISSUER,SUB);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+valid())).andExpect(status().isServiceUnavailable());
    }
    @Test void newIdentityIsUserAndDoesNotLinkByEmailOrGroups() throws Exception {
        when(identities.findByIssuerAndSubject(ISSUER,SUB)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+valid())).andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(captor.capture());
        assertEquals("USER",captor.getValue().getRole());assertEquals("ACTIVE",captor.getValue().getStatus());
        verify(users,never()).findByEmailIgnoreCase(any());verify(identities).saveAndFlush(any());
    }
    @Test void rejectsOtherRsaSignatureAndMissingRequiredTimes() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        var bad=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),JWTParser.parse(valid()).getJWTClaimsSet());
        bad.sign(new RSASSASigner((RSAPrivateKey)generator.generateKeyPair().getPrivate()));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+bad.serialize())).andExpect(status().isUnauthorized());
        var missingTimes=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject(SUB).claim("client_id","client").claim("token_use","access").build());
        missingTimes.sign(new RSASSASigner((RSAPrivateKey)keys.getPrivate()));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+missingTimes.serialize())).andExpect(status().isUnauthorized());
    }
    @Test void pendingProvisioningPolicyCreatesUserButBlocksApplication() throws Exception {
        when(identities.findByIssuerAndSubject(ISSUER,SUB)).thenReturn(Optional.empty());
        var service=new CognitoUsers(identities,users,"PENDING_APPROVAL",context.getBean(CognitoProfileClient.class));
        var auth=service.authenticate(context.getBean("testDecoder",JwtDecoder.class).decode(valid()));
        assertTrue(auth.getAuthorities().isEmpty());
        var captor=org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(captor.capture());
        assertEquals("PENDING_APPROVAL",captor.getValue().getStatus());assertEquals("USER",captor.getValue().getRole());
    }
}
