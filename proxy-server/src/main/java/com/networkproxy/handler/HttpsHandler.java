package com.networkproxy.handler;

import com.networkproxy.https.ConnectRequest;
import com.networkproxy.https.ConnectRequestParser;
import com.networkproxy.https.HttpsException;
import com.networkproxy.https.HttpsTunnelManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class HttpsHandler implements ProtocolHandler {

    private static final int MAX_HEADER_SIZE = 64 * 1024;
    private final ConnectRequestParser parser;
    private final HttpsTunnelManager tunnelManager;
    private final boolean enabled;

    public HttpsHandler(ConnectRequestParser parser,
                        HttpsTunnelManager tunnelManager,
                        boolean enabled) {
        this.parser = parser;
        this.tunnelManager = tunnelManager;
        this.enabled = enabled;
    }

    @Override
    public void handle(Socket clientSocket) throws IOException {
        handle(clientSocket,
                new BufferedInputStream(clientSocket.getInputStream()));
    }

    @Override
    public void handle(Socket clientSocket, BufferedInputStream input)
            throws IOException {
        OutputStream output = clientSocket.getOutputStream();
        try {
            if (!enabled) {
                sendError(output, 403, "HTTPS proxy is disabled");
                return;
            }
            byte[] headerBytes = readHeaders(input);
            if (headerBytes == null) {
                return;
            }
            String headerBlock =
                    new String(headerBytes, StandardCharsets.ISO_8859_1);
            String[] lines = headerBlock.split("\r\n", -1);
            if (lines.length == 0 || lines[0].isBlank()) {
                throw new HttpsException(400, "Invalid CONNECT request");
            }
            ConnectRequest request = parser.parse(lines[0]);
            Socket upstream;
            try {
                upstream = tunnelManager.connect(request);
            } catch (HttpsException e) {
                throw e;
            } catch (IOException e) {
                sendError(output, 502, "Unable to connect to destination");
                return;
            }
            try (upstream) {
                writeEstablished(output);
                try {
                    tunnelManager.relay(clientSocket, upstream);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("HTTPS tunnel interrupted", e);
                }
            }
        } catch (HttpsException e) {
            sendError(output, e.getStatusCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(output, 400, e.getMessage());
        } catch (IOException e) {
            sendError(output, 502, "Unable to connect to destination");
        }
    }

    private byte[] readHeaders(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            headers.write(current);
            if (headers.size() > MAX_HEADER_SIZE) {
                throw new HttpsException(400, "Headers are too large");
            }
            if (previous == '\r' && current == '\n') {
                byte[] data = headers.toByteArray();
                int length = data.length;
                if (length >= 4
                        && data[length - 4] == '\r'
                        && data[length - 3] == '\n'
                        && data[length - 2] == '\r'
                        && data[length - 1] == '\n') {
                    return data;
                }
            }
            previous = current;
        }
        return headers.size() == 0 ? null : headers.toByteArray();
    }

    private void writeEstablished(OutputStream output) throws IOException {
        output.write(("HTTP/1.1 200 Connection Established\r\n"
                + "Proxy-Agent: NetworkProxySystem\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    private void sendError(OutputStream output, int status, String message)
            throws IOException {
        byte[] body = (status + " " + message + "\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        String response = "HTTP/1.1 " + status + " " + message + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }
}
