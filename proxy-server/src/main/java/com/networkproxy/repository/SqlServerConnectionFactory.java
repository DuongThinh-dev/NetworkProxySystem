package com.networkproxy.repository;

import com.networkproxy.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

public final class SqlServerConnectionFactory {

    private final DatabaseConfig config;

    public SqlServerConnectionFactory(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Database config is required");
        }
        this.config = config;
    }

    public Connection openConnection() throws SQLException {
        if (!config.enabled()) {
            throw new IllegalStateException("Database access is disabled");
        }
        // Windows Integrated Security lấy danh tính từ tiến trình Java,
        // không cần username/password trong cấu hình.
        if (usesIntegratedSecurity()) {
            return DriverManager.getConnection(config.jdbcUrl());
        }

        if (config.username().isBlank()) {
            throw new IllegalStateException(
                    "Set NETWORK_PROXY_DB_USERNAME before enabling database access");
        }

        Properties credentials = new Properties();
        credentials.setProperty("user", config.username());
        credentials.setProperty("password", config.password());
        return DriverManager.getConnection(config.jdbcUrl(), credentials);
    }

    private boolean usesIntegratedSecurity() {
        return config.jdbcUrl().toLowerCase(Locale.ROOT)
                .contains("integratedsecurity=true");
    }
}
