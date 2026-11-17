package com.networkproxy.core;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import com.networkproxy.handler.HandlerDispatcher;
import com.networkproxy.handler.HttpHandler;
import com.networkproxy.handler.HttpsHandler;
import com.networkproxy.https.ConnectRequestParser;
import com.networkproxy.https.HttpsConnectionConfig;
import com.networkproxy.https.HttpsTunnelManager;
import com.networkproxy.network.DataRelay;
import com.networkproxy.config.ProxyConfig;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ConnectionManager connectionManager;
    private final int clientTimeout;
    private final HandlerDispatcher dispatcher;

    public ClientHandler(
            Socket clientSocket,
            ConnectionManager connectionManager,
            int clientTimeout
    ) {
        this(clientSocket, connectionManager, clientTimeout,
                new ProxyConfig(8080, 1, clientTimeout));
    }

    public ClientHandler(
            Socket clientSocket,
            ConnectionManager connectionManager,
            int clientTimeout,
            ProxyConfig config
    ) {

        this.clientSocket = clientSocket;
        this.connectionManager = connectionManager;
        this.clientTimeout = clientTimeout;
        this.dispatcher = new HandlerDispatcher();
        dispatcher.register(HandlerDispatcher.Protocol.HTTP, new HttpHandler());
        dispatcher.register(
                HandlerDispatcher.Protocol.HTTPS,
                new HttpsHandler(
                        new ConnectRequestParser(),
                        new HttpsTunnelManager(
                                new HttpsConnectionConfig(
                                        config.getUpstreamConnectTimeout(),
                                        config.getHttpsTunnelTimeout(),
                                        java.util.Set.of(443),
                                        config.isHttpsAllowPrivateDestinations()),
                                new DataRelay()),
                        config.isHttpsEnabled()));
    }

    @Override
    public void run() {

        String clientAddress =
                clientSocket.getInetAddress().getHostAddress();

        System.out.println("Client ket noi: " + clientAddress);

        try {

            clientSocket.setSoTimeout(clientTimeout);

            BufferedInputStream input =
                    new BufferedInputStream(clientSocket.getInputStream());
            input.mark(4096);
            String requestLine = readRequestLine(input);
            input.reset();
            HandlerDispatcher.Protocol protocol =
                    requestLine != null
                            && requestLine.regionMatches(true, 0, "CONNECT ", 0, 8)
                            ? HandlerDispatcher.Protocol.HTTPS
                            : HandlerDispatcher.Protocol.HTTP;
            dispatcher.dispatch(protocol, clientSocket, input);

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

    private String readRequestLine(BufferedInputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                if (line.length() > 0 && line.charAt(line.length() - 1) == '\r') {
                    line.setLength(line.length() - 1);
                }
                return line.toString();
            }
            if (line.length() >= 4095) {
                return null;
            }
            line.append((char) value);
        }
        return line.length() == 0 ? null : line.toString();
    }
}