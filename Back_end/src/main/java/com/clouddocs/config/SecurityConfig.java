package com.clouddocs.config;

import com.clouddocs.auth.CognitoUsers;
import com.clouddocs.auth.ProvisioningFailure;
import com.clouddocs.auth.ProvisioningErrorFilter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    JwtDecoder cognitoDecoder(@Value("${auth.cognito.issuer}") String issuer,
            @Value("${auth.cognito.client-id}") String clientId) {
        if (!issuer.matches("https://cognito-idp\\.[a-z0-9-]+\\.amazonaws\\.com/[A-Za-z0-9_-]+")
                || clientId.isBlank()) throw new IllegalArgumentException("Invalid Cognito configuration");
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), accessValidator(clientId)));
        return decoder;
    }
    public static OAuth2TokenValidator<Jwt> accessValidator(String clientId) {
        return jwt -> "access".equals(jwt.getClaimAsString("token_use"))
                && clientId.equals(jwt.getClaimAsString("client_id"))
                && jwt.getSubject() != null
                && jwt.getSubject().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http, CognitoUsers users) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/api/health", "/error").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/change-password").denyAll()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/notifications").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/notifications/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .anyRequest().hasAnyRole("ADMIN", "MANAGER", "USER"))
                .addFilterBefore(new ProvisioningErrorFilter(), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwt -> {
                    try { return users.authenticate(jwt); }
                    catch (DataIntegrityViolationException ex) { throw new ProvisioningFailure(409, ex); }
                    catch (DataAccessException ex) { throw new ProvisioningFailure(503, ex); }
                })))
                .build();
    }
}
