package com.clouddocs.storage;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorageController {
    private final StorageManagementService management;

    public StorageController(StorageManagementService management) {
        this.management=management;
    }

    @GetMapping("/api/storage/management")
    public StorageManagementService.Report management(Authentication authentication){return management.management(authentication.getName());}

    @GetMapping("/api/storage/usage")
    public StorageQuotaService.Usage usage(Authentication authentication) {
        return management.summary(authentication.getName());
    }
}
