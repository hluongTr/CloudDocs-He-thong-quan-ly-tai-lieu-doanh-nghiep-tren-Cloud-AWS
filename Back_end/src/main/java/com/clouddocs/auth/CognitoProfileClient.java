package com.clouddocs.auth;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Trusted server-side UserInfo. Never reads an unverified browser ID token. */
@Component
public class CognitoProfileClient {
    private final RestClient http;
    private final URI endpoint;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(Map<String, Object> claims, Instant until) {}
    public CognitoProfileClient(@Value("${auth.cognito.user-info-uri}") String uri) {
        endpoint = URI.create(uri);
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null || !"/oauth2/userInfo".equals(endpoint.getPath()))
            throw new IllegalArgumentException("Invalid trusted Cognito UserInfo endpoint");
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(3));
        http = RestClient.builder().requestFactory(factory).build();
    }
    @SuppressWarnings("unchecked")
    public Map<String, Object> profile(Jwt jwt) {
        String key = jwt.getIssuer() + "|" + jwt.getSubject();
        Cached existing = cache.get(key);
        if (existing != null && Instant.now().isBefore(existing.until())) return existing.claims();
        Map<String, Object> claims = Map.of();
        try {
            Map<String, Object> response = http.get().uri(endpoint).headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                    .retrieve().body(Map.class);
            if (response != null && jwt.getSubject().equals(response.get("sub"))) {
                Map<String, Object> selected = new HashMap<>();
                for (String field : List.of("sub", "name", "given_name", "family_name", "email", "email_verified")) {
                    Object value = response.get(field);
                    if (value instanceof String || value instanceof Boolean) selected.put(field, value);
                }
                claims = Map.copyOf(selected);
            }
        } catch (org.springframework.web.client.RestClientException ex) {
            // A profile outage must not grant rights, change identity, or turn into a token error.
        }
        if (cache.size() >= 10000) cache.clear();
        cache.put(key, new Cached(claims, Instant.now().plusSeconds(claims.isEmpty() ? 60 : 300)));
        return claims;
    }
}
