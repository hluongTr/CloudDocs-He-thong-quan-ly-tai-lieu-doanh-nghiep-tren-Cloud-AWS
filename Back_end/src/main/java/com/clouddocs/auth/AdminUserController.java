package com.clouddocs.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import com.clouddocs.document.DocumentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class AdminUserController {
    private final UserRepository users;
    private final DocumentRepository documents;
    private final com.clouddocs.work.WorkTaskRepository workTasks;

    public AdminUserController(UserRepository users, DocumentRepository documents, com.clouddocs.work.WorkTaskRepository workTasks) {
        this.users = users;
        this.documents = documents;
        this.workTasks = workTasks;
    }

    @GetMapping
    public List<UserDtos.AdminUserResponse> findAll() {
        return users.findCognitoUsers().stream().map(this::toResponse).toList();
    }

    @PatchMapping("/{id}/role")
    public UserDtos.AdminUserResponse updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request,
            Authentication authentication) {
        users.lockAdmins();
        User user = get(id);
        String role = request.role().toUpperCase();
        if (!List.of("USER","MANAGER","ADMIN").contains(role)) {
            throw new IllegalArgumentException("Role chỉ có USER, MANAGER hoặc ADMIN");
        }
        if (user.getUsername().equalsIgnoreCase(authentication.getName())
                && !role.equals("ADMIN")) {
            throw new IllegalStateException("Không thể tự hạ quyền tài khoản ADMIN đang đăng nhập");
        }
        if (user.getRole().equalsIgnoreCase("ADMIN") && !role.equals("ADMIN") && "ACTIVE".equalsIgnoreCase(user.getStatus()) && countActiveAdmins() <= 1) {
            throw new IllegalStateException("Hệ thống phải có ít nhất một ADMIN");
        }
        if(role.equals("MANAGER")&&!DepartmentScope.assigned(user))throw new IllegalStateException("Cần gán phòng ban trước khi đặt quyền Trưởng phòng");
        user.setRole(role);
        return toResponse(users.save(user));
    }

    @PatchMapping("/{id}/status")
    public UserDtos.AdminUserResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request,
            Authentication authentication) {
        users.lockAdmins();
        User user = get(id);
        String status = request.status().toUpperCase();
        if (!status.equals("ACTIVE") && !status.equals("LOCKED")) {
            throw new IllegalArgumentException("Status chỉ có ACTIVE hoặc LOCKED");
        }
        if (user.getUsername().equalsIgnoreCase(authentication.getName())
                && status.equals("LOCKED")) {
            throw new IllegalStateException("Không thể tự khóa tài khoản ADMIN đang đăng nhập");
        }
        if (user.getRole().equalsIgnoreCase("ADMIN")
                && "ACTIVE".equalsIgnoreCase(user.getStatus())
                && status.equals("LOCKED")
                && countActiveAdmins() <= 1) {
            throw new IllegalStateException("Hệ thống phải có ít nhất một ADMIN đang hoạt động");
        }
        user.setStatus(status);
        return toResponse(users.save(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        users.lockAdmins();
        User user = get(id);
        if (user.getUsername().equalsIgnoreCase(authentication.getName())) {
            throw new IllegalStateException("Không thể tự xóa tài khoản đang đăng nhập");
        }
        if (user.getRole().equalsIgnoreCase("ADMIN") && "ACTIVE".equalsIgnoreCase(user.getStatus()) && countActiveAdmins() <= 1) {
            throw new IllegalStateException("Không thể xóa ADMIN cuối cùng");
        }
        if (documents.existsByOwnerIdAndStatusNot(user.getId(), "PURGED")) {
            throw new IllegalStateException(
                    "Không thể xóa người dùng đang sở hữu tài liệu. Hãy chuyển/xóa tài liệu trước.");
        }
        if (workTasks.hasOpenTasks(user.getId())) throw new IllegalStateException("Tài khoản còn công việc chưa hoàn thành; cần xử lý xong trước khi xóa");
        // Preserve identity and audit references; a valid Cognito token must not recreate the account.
        user.setStatus("DELETED");
        users.save(user);
        return ResponseEntity.noContent().build();
    }

    private User get(Long id) {
        return users.findById(id)
                .filter(user -> !"DELETED".equalsIgnoreCase(user.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
    }

    private long countActiveAdmins() {
        return users.countActiveCognitoAdmins();
    }

    private UserDtos.AdminUserResponse toResponse(User u) {
        return new UserDtos.AdminUserResponse(
                u.getId(),
                u.getUsername(),
                ProfilePresentation.email(u),
                ProfilePresentation.name(u),
                u.getDepartment(),
                u.getRole(),
                u.getStatus(),
                u.getCreatedAt());
    }

    public record RoleRequest(@NotBlank @Pattern(regexp = "(?i)USER|MANAGER|ADMIN") String role) {}

    public record StatusRequest(@NotBlank @Pattern(regexp = "(?i)ACTIVE|LOCKED") String status) {}
}
