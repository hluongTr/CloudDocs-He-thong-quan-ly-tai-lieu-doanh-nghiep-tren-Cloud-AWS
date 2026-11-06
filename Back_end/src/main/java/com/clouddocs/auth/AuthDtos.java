package com.clouddocs.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 100) String department) {}

    public record UpdateDisplayNameRequest(@NotBlank @Size(max = 120) String fullName) {}

    public record UserResponse(
            Long id,
            String username,
            String email,
            String fullName,
            String department,
            String role,
            String status) {}



    public record MessageResponse(String message) {}
}
