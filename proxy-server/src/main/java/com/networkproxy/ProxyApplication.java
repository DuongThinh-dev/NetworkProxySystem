package com.networkproxy;

import com.networkproxy.config.ControlConfig;
import com.networkproxy.config.DatabaseConfig;
import com.networkproxy.config.ProxyConfig;
import com.networkproxy.control.ProxyControlServer;
import com.networkproxy.core.ConnectionManager;
import com.networkproxy.core.ProxyServer;
import com.networkproxy.logging.ConnectionLogger;
import com.networkproxy.repository.LogRepository;
import com.networkproxy.repository.RuleRepository;
import com.networkproxy.security.AccessControlService;
import com.networkproxy.security.RuleManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public class ProxyApplication {

    public static void main(String[] args) {

        Properties properties = loadProperties();
        ProxyConfig config = loadConfig(properties);
        DatabaseConfig databaseConfig = DatabaseConfig.from(properties);
        ControlConfig controlConfig = ControlConfig.from(properties);

        RuleManager ruleManager;
        AccessControlService accessControlService;
        ConnectionLogger connectionLogger;
        try {
            ruleManager = databaseConfig.enabled()
                    ? new RuleManager(new RuleRepository(databaseConfig))
                    : new RuleManager(List::of);
            accessControlService = new AccessControlService(ruleManager);

            // Khi database tắt, dùng no-op logger thay vì cố ghi log vào DB.
            connectionLogger = databaseConfig.enabled()
                    ? new ConnectionLogger(new LogRepository(databaseConfig))
                    : new ConnectionLogger(log -> { });
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Khong the khoi tao Access Control va Logging", e);
        }

        ConnectionManager connectionManager =
                new ConnectionManager(config, accessControlService, connectionLogger);

        ProxyServer proxyServer =
                new ProxyServer(config, connectionManager);

        ProxyControlServer controlServer = startControlServer(
                controlConfig, ruleManager, connectionManager);

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    System.out.println("\nDang tat Proxy Server...");
                    proxyServer.stop();
                    if (controlServer != null) {
                        controlServer.close();
                    }
                    connectionManager.shutdown();
                })
        );

        try {
            proxyServer.start();
        } catch (Exception e) {
            System.err.println(
                    "Khong the khoi dong Proxy Server: " + e.getMessage());
        } finally {
            proxyServer.stop();
            if (controlServer != null) {
                controlServer.close();
            }
            connectionManager.shutdown();
        }
    }

    private static ProxyControlServer startControlServer(
            ControlConfig controlConfig,
            RuleManager ruleManager,
            ConnectionManager connectionManager
    ) {
        if (!controlConfig.enabled()) {
            System.out.println("Proxy Control API: disabled");
            return null;
        }

        try {
            ProxyControlServer controlServer = new ProxyControlServer(
                    controlConfig.host(),
                    controlConfig.port(),
                    controlConfig.token(),
                    ruleManager,
                    connectionManager);
            controlServer.start();
            System.out.println("Proxy Control API: http://"
                    + controlConfig.host() + ":" + controlConfig.port());
            return controlServer;
        } catch (Exception e) {
            System.err.println(
                    "Khong the khoi dong Proxy Control API: " + e.getMessage());
            return null;
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = ProxyApplication.class
                .getResourceAsStream("/application.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing application.properties");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load proxy configuration", e);
        }
        return properties;
    }

    private static ProxyConfig loadConfig(Properties properties) {
        return new ProxyConfig(
                integer(properties, "proxy.port", 8080),
                integer(properties, "proxy.max-threads", 50),
                integer(properties, "proxy.client-timeout", 60000),
                integer(properties, "proxy.upstream.connect-timeout", 10000),
                Boolean.parseBoolean(properties.getProperty("https.enabled", "true")),
                Boolean.parseBoolean(properties.getProperty(
                        "https.allow-private-destinations", "false")),
                integer(properties, "https.tunnel-timeout", 60000),
                Boolean.parseBoolean(properties.getProperty("ssh.enabled", "true")),
                integer(properties, "ssh.default-port", 22),
                integer(properties, "ssh.connect-timeout", 10000),
                Path.of(expandHome(properties.getProperty(
                        "ssh.known-hosts-path",
                        "${user.home}/.ssh/known_hosts"))));
    }

    private static int integer(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key, Integer.toString(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric property: " + key, e);
        }
    }

    private static String expandHome(String value) {
        return value.replace("${user.home}", System.getProperty("user.home"));
    }
}
