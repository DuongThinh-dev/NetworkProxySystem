package com.networkproxy.config;

import java.util.Properties;

/**
 * Cấu hình cho control API nhỏ của proxy-server. Control API cho phép Admin
 * gửi lệnh reload rule mà không cần khởi động lại proxy.
 */
public record ControlConfig(
        boolean enabled,
        String host,
        int port,
        String token
) {

    public ControlConfig {
        host = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
        token = token == null ? "" : token;

        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(
                    "proxy.control.port phải nằm trong khoảng 1..65535");
        }
        if (enabled && token.isBlank()) {
            throw new IllegalArgumentException(
                    "proxy.control.token là bắt buộc khi bật proxy.control.enabled");
        }
    }

    public static ControlConfig from(Properties properties) {
        return new ControlConfig(
                booleanValue(properties,
                        "proxy.control.enabled",
                        "PROXY_CONTROL_ENABLED",
                        false),
                value(properties,
                        "proxy.control.host",
                        "PROXY_CONTROL_HOST",
                        "127.0.0.1"),
                integer(properties,
                        "proxy.control.port",
                        "PROXY_CONTROL_PORT",
                        8082),
                secret(properties,
                        "proxy.control.token",
                        "PROXY_CONTROL_TOKEN"));
    }

    private static String value(
            Properties properties,
            String propertyName,
            String environmentName,
            String fallback
    ) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return properties.getProperty(propertyName, fallback).trim();
    }

    private static String secret(Properties properties, String propertyName,
                                 String environmentName) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null
                ? properties.getProperty(propertyName, "")
                : environmentValue;
    }

    private static int integer(
            Properties properties,
            String propertyName,
            String environmentName,
            int fallback
    ) {
        String configured = value(properties, propertyName, environmentName,
                Integer.toString(fallback));
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Giá trị số không hợp lệ cho " + propertyName, exception);
        }
    }

    private static boolean booleanValue(
            Properties properties,
            String propertyName,
            String environmentName,
            boolean fallback
    ) {
        String configured = value(properties, propertyName, environmentName,
                Boolean.toString(fallback));
        if (!"true".equalsIgnoreCase(configured)
                && !"false".equalsIgnoreCase(configured)) {
            throw new IllegalArgumentException(
                    "Giá trị boolean không hợp lệ cho " + propertyName);
        }
        return Boolean.parseBoolean(configured);
    }
}
