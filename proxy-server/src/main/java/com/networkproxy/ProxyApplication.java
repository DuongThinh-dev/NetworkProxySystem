package com.networkproxy;

import com.networkproxy.config.ProxyConfig;
import com.networkproxy.core.ConnectionManager;
import com.networkproxy.core.ProxyServer;
import com.networkproxy.config.DatabaseConfig;
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

        AccessControlService accessControlService;
        ConnectionLogger connectionLogger;
        try {
            RuleManager ruleManager = databaseConfig.enabled()
                    ? new RuleManager(new RuleRepository(databaseConfig))
                    : new RuleManager(List::of);
            accessControlService = new AccessControlService(ruleManager);
            connectionLogger = new ConnectionLogger(
                    new LogRepository(databaseConfig));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Khong the khoi tao Access Control va Logging", e);
        }

        ConnectionManager connectionManager =
                new ConnectionManager(config, accessControlService, connectionLogger);

        // 3. Khởi tạo ProxyServer
        ProxyServer proxyServer =
                new ProxyServer(config, connectionManager);

        // 4. Đăng ký xử lý khi chương trình bị dừng
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    System.out.println(
                            "\nDang tat Proxy Server..."
                    );

                    proxyServer.stop();

                    connectionManager.shutdown();
                })
        );

        // 5. Khởi động Proxy Server
        try {

            proxyServer.start();

        } catch (Exception e) {

            System.err.println(
                    "Khong the khoi dong Proxy Server: "
                            + e.getMessage()
            );

        } finally {

            proxyServer.stop();

            connectionManager.shutdown();
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