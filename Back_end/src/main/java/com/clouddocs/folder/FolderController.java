package com.clouddocs.folder;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/folders")
public class FolderController {
    public record FolderRequest(@NotBlank @Size(max=120) String name, Long parentId) {}
    public record MoveRequest(@Size(min=1,max=500) List<Long> documentIds, Long folderId) {}
    private final FolderService service;
    public FolderController(FolderService service) { this.service = service; }
    @GetMapping public List<FolderService.FolderView> list(Authentication auth) { return service.list(auth.getName()); }
    @PostMapping public DocumentFolder create(@Valid @RequestBody FolderRequest body, Authentication auth) { return service.create(body.name(), body.parentId(), auth.getName()); }
    @PutMapping("/{id}") public DocumentFolder rename(@PathVariable Long id, @Valid @RequestBody FolderRequest body, Authentication auth) { return service.rename(id, body.name(), auth.getName()); }
    @PostMapping("/move") public void move(@Valid @RequestBody MoveRequest body, Authentication auth) { service.move(body.documentIds(), body.folderId(), auth.getName()); }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id, Authentication auth) { service.delete(id, auth.getName()); }
}
