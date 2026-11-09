package com.networkproxy.core;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import com.networkproxy.handler.HttpHandler;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ConnectionManager connectionManager;
    private final int clientTimeout;

    public ClientHandler(
            Socket clientSocket,
            ConnectionManager connectionManager,
            int clientTimeout
    ) {

        this.clientSocket = clientSocket;
        this.connectionManager = connectionManager;
        this.clientTimeout = clientTimeout;
    }

    @Override
    public void run() {

        String clientAddress =
                clientSocket.getInetAddress().getHostAddress();

        System.out.println("Client ket noi: " + clientAddress);

        try {

            clientSocket.setSoTimeout(clientTimeout);

            new HttpHandler().handle(clientSocket);

        } catch (SocketTimeoutException e) {

            System.out.println(
                    "Client timeout: " + clientAddress
            );

        } catch (IOException e) {

            System.err.println(
                    "Loi Client " + clientAddress +
                    ": " + e.getMessage()
            );

        } finally {

            System.out.println(
                    "Client ngat ket noi: " + clientAddress
            );

            connectionManager.removeClient(clientSocket);
        }
    }
}