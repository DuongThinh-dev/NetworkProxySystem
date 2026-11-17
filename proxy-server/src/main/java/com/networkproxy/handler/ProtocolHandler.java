package com.networkproxy.handler;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.net.Socket;

//Đây là interface chung mà các Handler sẽ triển khai.
public interface ProtocolHandler {

    void handle(Socket clientSocket) throws IOException;

    default void handle(Socket clientSocket, BufferedInputStream input)
            throws IOException {
        handle(clientSocket);
    }
}