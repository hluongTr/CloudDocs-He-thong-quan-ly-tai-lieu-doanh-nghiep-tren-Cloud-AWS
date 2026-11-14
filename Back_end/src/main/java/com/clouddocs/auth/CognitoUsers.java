package com.clouddocs.auth;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Service
public class CognitoUsers {
    private final CognitoIdentityRepository identities;
    private final UserRepository users;
    private final String initialStatus;
    private final CognitoProfileClient profiles;
    public CognitoUsers(CognitoIdentityRepository identities, UserRepository users,
            @Value("${auth.cognito.new-user-status:ACTIVE}") String initialStatus, CognitoProfileClient profiles) {
        if (!List.of("ACTIVE", "PENDING_APPROVAL").contains(initialStatus))
            throw new IllegalArgumentException("Invalid initial user status");
        this.identities = identities; this.users = users; this.initialStatus = initialStatus;
        this.profiles = profiles;
    }
    @Transactional
    public AbstractAuthenticationToken authenticate(Jwt jwt) {
        User user = identities.findByIssuerAndSubject(jwt.getIssuer().toString(), jwt.getSubject())
                .map(CognitoIdentity::getUser).orElseGet(() -> provision(jwt));
        if ("ACTIVE".equalsIgnoreCase(user.getStatus()) &&
                (ProfilePresentation.missingName(user.getFullName()) || ProfilePresentation.missingEmail(user.getEmail()))) {
            if (fillMissingProfile(user, jwt)) users.saveAndFlush(user);
        }
        String role = user.getRole() == null ? "" : user.getRole().toUpperCase(Locale.ROOT);
        var authorities = "ACTIVE".equalsIgnoreCase(user.getStatus()) && List.of("ADMIN", "MANAGER", "USER").contains(role)
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                : List.<SimpleGrantedAuthority>of();
        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }
    private User provision(Jwt jwt) {
        // Access tokens need not contain email/name. Never link existing users by email.
        String key = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("c_" + key);
        user.setPassword("!COGNITO_ONLY!"); // Historical NOT NULL column, no usable password.
        user.setFullName("");
        user.setDepartment("Chưa cập nhật");
        user.setRole("USER"); user.setStatus(initialStatus);
        fillMissingProfile(user, jwt);
        users.saveAndFlush(user);
        identities.saveAndFlush(new CognitoIdentity(jwt.getIssuer().toString(), jwt.getSubject(), user));
        return user;
    }

    private boolean fillMissingProfile(User user, Jwt jwt) {
        var claims = profiles.profile(jwt);
        if (claims == null || !jwt.getSubject().equals(claims.get("sub"))) return false;
        boolean changed = false;
        String name = text(claims.get("name"));
        if (name.isBlank()) name = (text(claims.get("given_name")) + " " + text(claims.get("family_name"))).trim();
        if (ProfilePresentation.missingName(user.getFullName()) && !ProfilePresentation.missingName(name) && name.length() <= 120) {
            user.setFullName(name); changed = true;
        }
        String email = text(claims.get("email")).toLowerCase(Locale.ROOT);
        boolean verified = Boolean.TRUE.equals(claims.get("email_verified")) || "true".equals(claims.get("email_verified"));
        if (ProfilePresentation.missingEmail(user.getEmail()) && verified && email.length() <= 150
                && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            var owner = users.findByEmailIgnoreCase(email);
            if (owner.isEmpty() || java.util.Objects.equals(owner.get().getId(), user.getId())) {
                user.setEmail(email); changed = true;
            }
        }
        return changed;
    }
    private String text(Object value) { return value instanceof String s ? s.trim() : ""; }
}
