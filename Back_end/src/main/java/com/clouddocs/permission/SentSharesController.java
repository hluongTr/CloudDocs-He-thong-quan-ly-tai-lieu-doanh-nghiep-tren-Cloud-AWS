package com.clouddocs.permission;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;
@RestController @RequestMapping("/api/shares/sent")
public class SentSharesController {
 private final DocumentPermissionService service;
 public SentSharesController(DocumentPermissionService service){this.service=service;}
 @GetMapping public List<DocumentPermissionDtos.SentShare> list(Authentication authentication){return service.listSent(authentication.getName());}
}
