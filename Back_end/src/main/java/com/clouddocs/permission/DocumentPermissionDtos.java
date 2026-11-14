package com.clouddocs.permission;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class DocumentPermissionDtos {
    private DocumentPermissionDtos() {}

    public record PermissionRequest(@NotNull DocumentPermissionType permission) {}

    public record PermissionResponse(
            Long id,
            Long documentId,
            Long userId,
            String username,
            String fullName,
            String email,
            String permission,
            Instant createdAt) {}

    public record SentShare(Long id,Long documentId,String documentName,String fileName,String fileType,String status,Long userId,String fullName,String email,String department,String permission,Instant createdAt,boolean canManage){}

    public record ShareableUserResponse(
            Long id, String username, String fullName, String email, String department) {}
}
