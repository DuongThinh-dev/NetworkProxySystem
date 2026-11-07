package com.networkproxy.network;

import java.io.IOException;
import java.net.Socket;

public class SocketManager {

    public void closeSocket(Socket socket) {

        if (socket == null) {
            return;
        }

        try {
            if (!socket.isClosed()) {
                socket.close();
                System.out.println("Da dong Socket: " + socket);
            }
        } catch (IOException e) {
            System.err.println("Loi khi dong Socket: " + e.getMessage());
        }
    }
}