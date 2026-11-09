package com.networkproxy.core;

import com.networkproxy.config.ProxyConfig;
import com.networkproxy.network.SocketManager;

import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ConnectionManager {

    private final ExecutorService executorService;

    private final Set<Socket> activeClients =
            ConcurrentHashMap.newKeySet();

    private final SocketManager socketManager;

    public ConnectionManager(ProxyConfig config) {

        this.executorService = Executors.newFixedThreadPool(
                config.getMaxThreads()
        );

        this.socketManager = new SocketManager();
    }

    // Tiếp nhận và xử lý một Client
    public void handleClient(Socket clientSocket, int clientTimeout) {

        activeClients.add(clientSocket);

        try {

            executorService.execute(
                    new ClientHandler(
                            clientSocket,
                            this,
                            clientTimeout
                    )
            );

        } catch (RejectedExecutionException e) {

            System.err.println("Khong the tiep nhan Client moi: "
                    + e.getMessage());

            removeClient(clientSocket);
        }
    }

    // Xóa Client khỏi danh sách kết nối
    public void removeClient(Socket clientSocket) {

        activeClients.remove(clientSocket);

        socketManager.closeSocket(clientSocket);
    }

    // Lấy số lượng Client đang kết nối
    public int getActiveClientCount() {
        return activeClients.size();
    }

    // Đóng tất cả kết nối và dừng ExecutorService
    public void shutdown() {

        System.out.println("Dang dong cac ket noi...");

        for (Socket socket : activeClients) {
            socketManager.closeSocket(socket);
        }

        activeClients.clear();

        executorService.shutdownNow();

        System.out.println("ConnectionManager da dung.");
    }
}