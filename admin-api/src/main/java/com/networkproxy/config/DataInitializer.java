package com.networkproxy.config;

import com.networkproxy.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DataInitializer.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public DataInitializer(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            AdminProperties properties
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        try {
            if (adminUserRepository.count() > 0) {
                return;
            }

            adminUserRepository.insert(
                    properties.defaultUsername(),
                    passwordEncoder.encode(properties.defaultPassword()),
                    properties.defaultDisplayName());

            log.warn("Đã tạo tài khoản quản trị mặc định '{}'. "
                            + "Hãy đổi mật khẩu sau khi đăng nhập.",
                    properties.defaultUsername());
        } catch (DataAccessException exception) {
            log.error("Không thể khởi tạo bảng AdminUsers. "
                    + "Hãy chạy database/create-tables.sql và "
                    + "database/indexes.sql trước khi khởi động Admin API.",
                    exception);
            throw exception;
        }
    }
}
