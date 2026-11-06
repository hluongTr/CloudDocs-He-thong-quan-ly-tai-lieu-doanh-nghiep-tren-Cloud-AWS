package com.clouddocs.auth;

import com.clouddocs.auth.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {

        return service.me(authentication.getName());
    }

    @PutMapping("/me")
    public UserResponse updateProfile(
            Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(authentication.getName(), request);
    }

    @PutMapping("/me/display-name")
    public UserResponse updateDisplayName(Authentication authentication,
            @Valid @RequestBody UpdateDisplayNameRequest request) {
        return service.updateDisplayName(authentication.getName(), request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(Authentication authentication) {

        service.logout(authentication.getName());

        return new MessageResponse("Đã ghi nhận đăng xuất.");
    }

}
