package com.clouddocs.document;

import com.clouddocs.document.DocumentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentResponse> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String department,
            Authentication authentication) {
        return service.findAll(q, type, department, authentication.getName());
    }

    @GetMapping("/shared")
    public List<DocumentResponse> findShared(Authentication authentication) {
        return service.findShared(authentication.getName());
    }

    @GetMapping("/recent")
    public List<DocumentResponse> findRecent(Authentication authentication) {
        return service.findRecent(authentication.getName());
    }

    @GetMapping("/trash")
    public List<DocumentResponse> trash(Authentication authentication) {
        return service.findTrash(authentication.getName());
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id, Authentication authentication) {
        service.restore(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> purge(@PathVariable Long id, Authentication authentication) {
        service.purge(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public DocumentResponse findById(@PathVariable Long id, Authentication authentication) {
        return service.findById(id, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request, Authentication authentication) {
        DocumentResponse created = service.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/documents/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public DocumentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request,
            Authentication authentication) {
        return service.update(id, request, authentication.getName());
    }

    @PostMapping(value="/{id}/replacement",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse replaceFile(@PathVariable Long id,@RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UpdateDocumentRequest request,Authentication authentication){
        return service.replaceFile(id,file,request,authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadLocalFile(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        service.uploadLocalFile(id, file, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadLocalFile(
            @PathVariable Long id, Authentication authentication) {
        var result = service.downloadLocalFile(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename(result.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentLength(result.contentLength())
                .body(result.resource());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> previewLocalFile(
            @PathVariable Long id, Authentication authentication) {

        var result = service.previewLocalFile(id, authentication.getName());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline().filename(result.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(result.resource());
    }

    @PostMapping("/{id}/upload-complete")
    public ResponseEntity<Void> completeS3Upload(
            @PathVariable Long id, Authentication authentication) {

        service.completeS3Upload(id, authentication.getName());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/upload-url")
    public PresignedUploadResponse uploadUrl(@PathVariable Long id, Authentication authentication) {
        return service.createUploadUrl(id, authentication.getName());
    }

    @GetMapping("/{id}/download-url")
    public PresignedDownloadResponse downloadUrl(
            @PathVariable Long id, Authentication authentication) {
        return service.createDownloadUrl(id, authentication.getName());
    }

    @GetMapping("/{id}/preview-url")
    public PresignedDownloadResponse previewUrl(
            @PathVariable Long id, Authentication authentication) {

        return service.createPreviewUrl(id, authentication.getName());
    }
}
