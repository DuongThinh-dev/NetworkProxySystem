package com.networkproxy.handler;

import java.io.IOException;
import java.net.Socket;

public class SshHandler implements ProtocolHandler {

    @Override
    public void handle(Socket clientSocket) throws IOException {
        throw new IOException(
                "Inbound SSH is not supported; use the outbound SSH client service");
    }
}
