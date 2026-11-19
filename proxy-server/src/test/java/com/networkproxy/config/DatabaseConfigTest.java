package com.networkproxy.config;

import com.networkproxy.repository.SqlServerConnectionFactory;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @Test
    void databaseDisabledUsesSafeConfiguration() {
        Properties properties = new Properties();
        properties.setProperty("database.enabled", "false");
        properties.setProperty(
                "database.url",
                "jdbc:sqlserver://localhost:1433;databaseName=NetworkProxyDB;");
        properties.setProperty("database.username", "");
        properties.setProperty("database.password", "");

        DatabaseConfig config = DatabaseConfig.from(properties);

        assertFalse(config.enabled());
        assertTrue(config.username().isBlank());
    }

    @Test
    void disabledDatabaseConnectionIsNotOpened() {
        DatabaseConfig config = new DatabaseConfig(
                false,
                "jdbc:sqlserver://localhost:1433;databaseName=NetworkProxyDB;",
                "sa",
                "password");
        SqlServerConnectionFactory factory =
                new SqlServerConnectionFactory(config);

        assertThrows(IllegalStateException.class, factory::openConnection);
    }

    @Test
    void rejectsInvalidJdbcUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatabaseConfig(
                        false,
                        "jdbc:mysql://localhost/test",
                        "",
                        ""));
    }

    @Test
    void rejectsInvalidDatabaseEnabledValue() {
        Properties properties = new Properties();
        properties.setProperty("database.enabled", "maybe");

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.from(properties));
    }
}
