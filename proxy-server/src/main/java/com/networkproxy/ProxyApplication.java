package com.networkproxy;

import com.networkproxy.config.ProxyConfig;
import com.networkproxy.core.ConnectionManager;
import com.networkproxy.core.ProxyServer;

public class ProxyApplication {

    public static void main(String[] args) {

        // 1. Khởi tạo cấu hình
        ProxyConfig config = new ProxyConfig(
                8080,
                50,
                60000
        );

        // 2. Khởi tạo ConnectionManager
        ConnectionManager connectionManager =
                new ConnectionManager(config);

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
}