package com.networkproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        String defaultUsername,
        String defaultPassword,
        String defaultDisplayName
) {

    public AdminProperties {
        defaultUsername = blank(defaultUsername) ? "admin" : defaultUsername.trim();
        defaultPassword = blank(defaultPassword) ? "admin123" : defaultPassword;
        defaultDisplayName = blank(defaultDisplayName)
                ? "Quản trị viên"
                : defaultDisplayName.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
