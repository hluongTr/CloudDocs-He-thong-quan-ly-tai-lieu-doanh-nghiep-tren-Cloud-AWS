package com.clouddocs.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Handles only identity lookup/provisioning failures, never document/DB/S3 business errors. */
public class ProvisioningErrorFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try { chain.doFilter(request, response); }
        catch (ProvisioningFailure failure) {
            response.setStatus(failure.status());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(failure.status() == 409
                    ? "{\"error\":\"IDENTITY_CONFLICT\",\"message\":\"Xung đột liên kết danh tính. Vui lòng thử lại.\"}"
                    : "{\"error\":\"PROVISIONING_UNAVAILABLE\",\"message\":\"Không thể đọc hoặc tạo hồ sơ người dùng.\"}");
        }
    }
}
