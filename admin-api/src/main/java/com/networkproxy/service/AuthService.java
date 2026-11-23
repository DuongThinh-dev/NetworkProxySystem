package com.networkproxy.service;

import com.networkproxy.entity.AdminUser;
import com.networkproxy.exception.ApiException;
import com.networkproxy.repository.AdminUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminUser authenticate(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        AdminUser user = adminUserRepository.findByUsername(normalizedUsername)
                .orElseThrow(this::invalidCredentials);

        if (!user.enabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Tài khoản quản trị đã bị khóa");
        }
        if (password == null
                || !passwordEncoder.matches(password, user.passwordHash())) {
            throw invalidCredentials();
        }

        adminUserRepository.updateLastLogin(user.userId());
        return adminUserRepository.findById(user.userId()).orElse(user);
    }

    public AdminUser getById(long userId) {
        return adminUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài khoản quản trị"));
    }

    public void changePassword(
            long userId,
            String currentPassword,
            String newPassword
    ) {
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài khoản quản trị"));

        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        adminUserRepository.updatePassword(
                userId,
                passwordEncoder.encode(newPassword));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                "Tên đăng nhập hoặc mật khẩu không đúng");
    }
}
