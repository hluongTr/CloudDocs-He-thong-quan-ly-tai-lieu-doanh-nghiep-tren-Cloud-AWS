package com.clouddocs.document;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents/upload")
public class DocumentUploadController {
    private final DocumentUploadService service;
    public DocumentUploadController(DocumentUploadService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDtos.DocumentResponse upload(@RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") DocumentDtos.CreateDocumentRequest metadata,
            @RequestParam UUID requestId,@RequestParam(required=false) Long folderId,Authentication actor) {
        return service.upload(file,metadata,requestId,folderId,actor.getName());
    }
}
