package com.clouddocs.permission;

import com.clouddocs.permission.DocumentPermissionDtos.PermissionRequest;
import com.clouddocs.permission.DocumentPermissionDtos.PermissionResponse;
import com.clouddocs.permission.DocumentPermissionDtos.ShareableUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
public class DocumentPermissionController {

    private final DocumentPermissionService service;

    public DocumentPermissionController(DocumentPermissionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PermissionResponse> list(
            @PathVariable Long documentId, Authentication authentication) {
        return service.list(documentId, authentication.getName());
    }

    @GetMapping("/users")
    public List<ShareableUserResponse> shareableUsers(
            @PathVariable Long documentId, Authentication authentication) {
        return service.listShareableUsers(documentId, authentication.getName());
    }

    @PutMapping("/{userId}")
    public PermissionResponse grantOrUpdate(
            @PathVariable Long documentId,
            @PathVariable Long userId,
            @Valid @RequestBody PermissionRequest request,
            Authentication authentication) {
        return service.grantOrUpdate(documentId, userId, request, authentication.getName());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(
            @PathVariable Long documentId,
            @PathVariable Long userId,
            Authentication authentication) {
        service.revoke(documentId, userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
