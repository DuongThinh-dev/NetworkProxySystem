package com.networkproxy.handler;

import java.io.IOException;
import java.net.Socket;

//Đây là interface chung mà các Handler sẽ triển khai.
public interface ProtocolHandler {

    void handle(Socket clientSocket) throws IOException;
}