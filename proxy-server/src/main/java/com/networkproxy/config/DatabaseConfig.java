package com.networkproxy.config;

import java.util.Properties;

public record DatabaseConfig(
        boolean enabled,
        String jdbcUrl,
        String username,
        String password
) {

    public DatabaseConfig {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlserver://")) {
            throw new IllegalArgumentException("A SQL Server JDBC URL is required");
        }
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }

    public static DatabaseConfig from(Properties properties) {
        return new DatabaseConfig(
                booleanValue(properties, "database.enabled", "NETWORK_PROXY_DB_ENABLED", false),
                value(properties, "database.url", "NETWORK_PROXY_DB_URL",
                        "jdbc:sqlserver://localhost:1433;databaseName=NetworkProxyDB;"
                                + "encrypt=true;trustServerCertificate=false;loginTimeout=10"),
                value(properties, "database.username", "NETWORK_PROXY_DB_USERNAME", ""),
                secretValue(properties, "database.password", "NETWORK_PROXY_DB_PASSWORD")
        );
    }

    private static String value(Properties properties, String propertyName,
                                String environmentName, String fallback) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return properties.getProperty(propertyName, fallback).trim();
    }

    private static String secretValue(Properties properties, String propertyName,
                                      String environmentName) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null
                ? properties.getProperty(propertyName, "")
                : environmentValue;
    }

    private static boolean booleanValue(Properties properties, String propertyName,
                                        String environmentName, boolean fallback) {
        String environmentValue = System.getenv(environmentName);
        String configured = environmentValue == null || environmentValue.isBlank()
                ? properties.getProperty(propertyName, Boolean.toString(fallback))
                : environmentValue;
        if (!"true".equalsIgnoreCase(configured)
                && !"false".equalsIgnoreCase(configured)) {
            throw new IllegalArgumentException("Invalid boolean setting: " + propertyName);
        }
        return Boolean.parseBoolean(configured);
    }
}
