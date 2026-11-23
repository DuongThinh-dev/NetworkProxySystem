package com.networkproxy.controller;

import com.networkproxy.auth.AdminPrincipal;
import com.networkproxy.auth.AuthInterceptor;
import com.networkproxy.dto.AdminResponse;
import com.networkproxy.dto.ChangePasswordRequest;
import com.networkproxy.dto.LoginRequest;
import com.networkproxy.entity.AdminUser;
import com.networkproxy.service.AuditService;
import com.networkproxy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public AdminResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AdminUser user = authService.authenticate(
                request.username(),
                request.password());

        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        AdminPrincipal principal = new AdminPrincipal(
                user.userId(),
                user.username(),
                user.displayName());
        session.setAttribute(AuthInterceptor.SESSION_KEY, principal);

        auditService.record(principal, "LOGIN", "SESSION", null,
                "Đăng nhập từ " + httpRequest.getRemoteAddr());
        return AdminResponse.from(user);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object attribute = session.getAttribute(AuthInterceptor.SESSION_KEY);
            if (attribute instanceof AdminPrincipal principal) {
                auditService.record(principal, "LOGOUT", "SESSION", null,
                        "Đăng xuất");
            }
            session.invalidate();
        }
        return Map.of("message", "Đã đăng xuất");
    }

    @GetMapping("/me")
    public AdminResponse me(HttpSession session) {
        AdminPrincipal principal = principal(session);
        return AdminResponse.from(authService.getById(principal.userId()));
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpSession session
    ) {
        AdminPrincipal principal = principal(session);
        authService.changePassword(
                principal.userId(),
                request.currentPassword(),
                request.newPassword());
        auditService.record(principal, "CHANGE_PASSWORD", "ADMIN_USER",
                principal.userId(), "Đổi mật khẩu");
        return Map.of("message", "Đã đổi mật khẩu");
    }

    private AdminPrincipal principal(HttpSession session) {
        return (AdminPrincipal) session.getAttribute(AuthInterceptor.SESSION_KEY);
    }
}
