package com.clouddocs.auth;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record AdminUserResponse(
            Long id,
            String username,
            String email,
            String fullName,
            String department,
            String role,
            String status,
            Instant createdAt) {}
}
