package com.clouddocs.favorite;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class DocumentFavoriteController {
    private final DocumentFavoriteService service;

    public DocumentFavoriteController(DocumentFavoriteService service) {
        this.service = service;
    }

    @GetMapping
    public List<Long> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<Void> add(@PathVariable Long documentId, Authentication auth) {
        service.add(documentId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> remove(@PathVariable Long documentId, Authentication auth) {
        service.remove(documentId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
