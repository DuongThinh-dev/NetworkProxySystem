package com.networkproxy.core;

import com.networkproxy.config.ProxyConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class ProxyServer {

    private final ProxyConfig config;
    private final ConnectionManager connectionManager;

    private ServerSocket serverSocket;
    private volatile boolean running;

    public ProxyServer(
            ProxyConfig config,
            ConnectionManager connectionManager
    ) {

        this.config = config;
        this.connectionManager = connectionManager;
    }

    // Khởi động Proxy Server
    public void start() throws IOException {

        serverSocket = new ServerSocket();

        serverSocket.setReuseAddress(true);

        serverSocket.bind(
                new java.net.InetSocketAddress(config.getPort())
        );

        running = true;

        System.out.println("--------------------------------");
        System.out.println("      JAVA PROXY SERVER");
        System.out.println("--------------------------------");
        System.out.println("Proxy dang chay tai port: "
                + config.getPort());

        while (running) {

            try {

                // Chờ Client kết nối
                Socket clientSocket = serverSocket.accept();

                clientSocket.setTcpNoDelay(true);

                System.out.println(
                        "Chap nhan ket noi tu: "
                        + clientSocket.getInetAddress().getHostAddress()
                );

                // Chuyển Client cho ConnectionManager xử lý
                connectionManager.handleClient(
                        clientSocket,
                        config.getClientTimeout()
                );

            } catch (SocketException e) {

                if (running) {
                    System.err.println(
                            "Loi Socket: " + e.getMessage()
                    );
                }

                break;

            } catch (IOException e) {

                if (running) {
                    System.err.println(
                            "Loi tiep nhan Client: " + e.getMessage()
                    );
                }
            }
        }

        System.out.println("Proxy Server da dung.");
    }

    // Dừng Proxy Server
    public void stop() {

        running = false;

        try {

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

        } catch (IOException e) {

            System.err.println(
                    "Loi khi dong ServerSocket: " + e.getMessage()
            );
        }
    }
}