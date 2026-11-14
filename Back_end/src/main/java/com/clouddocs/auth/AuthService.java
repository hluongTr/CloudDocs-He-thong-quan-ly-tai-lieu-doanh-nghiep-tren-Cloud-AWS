package com.clouddocs.auth;

import com.clouddocs.auth.AuthDtos.*;
import com.clouddocs.audit.AuditLogService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Locale;

@Service
public class AuthService {

    private final com.clouddocs.department.DepartmentService departments;
    private final UserRepository users;
    private final AuditLogService auditLogService;
    public AuthService(UserRepository users, AuditLogService auditLogService, com.clouddocs.department.DepartmentService departments) {
        this.users = users;
        this.departments=departments;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void logout(String username) {

        auditLogService.log(username, "LOGOUT", null, null, username + " đăng xuất hệ thống");
    }

    @Transactional(readOnly = true)
    public UserResponse me(String username) {

        return toResponse(
                users.findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản")));
    }

    @Transactional
    public UserResponse updateDisplayName(String username, UpdateDisplayNameRequest request) {
        User user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Tài khoản không thể cập nhật tên hiển thị");
        }
        String name = request.fullName().trim();
        if (ProfilePresentation.missingName(name) || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Vui lòng nhập tên hiển thị của bạn");
        }
        user.setFullName(name);
        users.saveAndFlush(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(String username, UpdateProfileRequest request) {
        User user =
                users.findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Tài khoản đang bị khóa không thể cập nhật hồ sơ");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email)
                .ifPresent(
                        existing -> {
                            if (!existing.getId().equals(user.getId())) {
                                throw new IllegalArgumentException("Email đã được sử dụng");
                            }
                        });
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setDepartment(departments.profileName(user,request.department()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Covers concurrent requests claiming the same unique email.
            throw new IllegalStateException("Email đã được sử dụng. Vui lòng chọn email khác.", ex);
        }
        return toResponse(user);
    }

    private UserResponse toResponse(User u) {

        return new UserResponse(
                u.getId(),
                u.getUsername(),
                ProfilePresentation.email(u),
                ProfilePresentation.name(u),
                u.getDepartment(),
                u.getRole(),
                u.getStatus(),u.getAvatarUpdatedAt());
    }
}
