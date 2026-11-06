package com.clouddocs.storage;

import com.clouddocs.auth.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorageController {
    private final UserRepository users;
    private final StorageQuotaService quotas;

    public StorageController(UserRepository users, StorageQuotaService quotas) {
        this.users = users;
        this.quotas = quotas;
    }

    @GetMapping("/api/storage/usage")
    public StorageQuotaService.Usage usage(Authentication authentication) {
        return quotas.usage(users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow());
    }
}
