package com.clouddocs.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
public class AvatarController {
 private final AvatarService avatars;private final AuthService auth;
 public AvatarController(AvatarService avatars,AuthService auth){this.avatars=avatars;this.auth=auth;}
 @PostMapping(value="/api/auth/me/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public AuthDtos.UserResponse upload(Authentication a,@RequestParam("file") MultipartFile file){avatars.upload(a.getName(),file);return auth.me(a.getName());}
 @DeleteMapping("/api/auth/me/avatar") public AuthDtos.UserResponse delete(Authentication a){avatars.delete(a.getName());return auth.me(a.getName());}
 @GetMapping(value="/api/avatars/{id}",produces=MediaType.IMAGE_PNG_VALUE) public ResponseEntity<byte[]> read(Authentication a,@PathVariable Long id){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(avatars.read(a.getName(),id));}
}
