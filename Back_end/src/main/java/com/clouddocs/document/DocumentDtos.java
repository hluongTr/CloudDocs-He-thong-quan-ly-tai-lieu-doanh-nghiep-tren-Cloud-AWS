package com.clouddocs.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record CreateDocumentRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\r\\n\\x00]+") String fileName,
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,49}") String fileType,
            @NotNull @PositiveOrZero Long fileSize,
            @NotBlank @Size(max = 100) String department) {}

    public record UpdateDocumentRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String department,
            @Pattern(regexp = "(?i)ACTIVE|SHARED|ARCHIVED") String status) {}

    public record DocumentResponse(
            Long id,
            String title,
            String description,
            String fileName,
            String fileType,
            Long fileSize,
            String owner,
            String department,
            String status,
            String updatedAt,
            String access,boolean inLibrary) {
        public DocumentResponse(Long id,String title,String description,String fileName,String fileType,Long fileSize,String owner,String department,String status,String updatedAt,String access){
            this(id,title,description,fileName,fileType,fileSize,owner,department,status,updatedAt,access,"OWNER".equals(access)||"ADMIN".equals(access));
        }
    }

    public record PresignedUploadResponse(
            Long documentId, String key, String uploadUrl, long expiresInSeconds) {}

    public record PresignedDownloadResponse(
            Long documentId, String downloadUrl, long expiresInSeconds) {}
}
