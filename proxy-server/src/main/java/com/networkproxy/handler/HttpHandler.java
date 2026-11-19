package com.networkproxy.handler;

import com.networkproxy.logging.ConnectionLog;
import com.networkproxy.logging.ConnectionLogger;
import com.networkproxy.security.AccessControlService;
import com.networkproxy.security.AccessRule;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class HttpHandler implements ProtocolHandler {

    private static final int MAX_HEADER_SIZE = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private final AccessControlService accessControlService;
    private final ConnectionLogger connectionLogger;

    public HttpHandler() {
        this(null, null);
    }

    public HttpHandler(AccessControlService accessControlService,
                       ConnectionLogger connectionLogger) {
        this.accessControlService = accessControlService;
        this.connectionLogger = connectionLogger;
    }

    @Override
    public void handle(Socket clientSocket) throws IOException {
        BufferedInputStream clientInput =
                new BufferedInputStream(clientSocket.getInputStream());
        handle(clientSocket, clientInput);
    }

    @Override
    public void handle(Socket clientSocket, BufferedInputStream clientInput)
            throws IOException {
        OutputStream clientOutput = clientSocket.getOutputStream();

        byte[] headerBytes = readHeaders(clientInput);
        if (headerBytes == null) {
            return;
        }

        String headers = new String(headerBytes, StandardCharsets.ISO_8859_1);
        String[] lines = headers.split("\r\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) {
            sendError(clientOutput, 400, "Bad Request");
            return;
        }

        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length != 3) {
            sendError(clientOutput, 400, "Bad Request");
            return;
        }

        URI target = resolveTarget(requestLine[1], lines);
        if (target == null || target.getHost() == null
                || !"http".equalsIgnoreCase(target.getScheme())) {
            sendError(clientOutput, 400, "HTTP proxy requires an absolute http URL");
            return;
        }

        int port = target.getPort() == -1 ? 80 : target.getPort();
        long contentLength = getContentLength(lines);
        if (contentLength < 0 || contentLength > Integer.MAX_VALUE) {
            sendError(clientOutput, 400, "Invalid Content-Length");
            return;
        }
        if (hasChunkedEncoding(lines)) {
            sendError(clientOutput, 501, "Chunked requests are not supported");
            return;
        }

        if (accessControlService != null) {
            AccessControlService.AccessRequest accessRequest =
                    new AccessControlService.AccessRequest(
                            clientSocket.getInetAddress().getHostAddress(),
                            target.getHost(),
                            isIpLiteral(target.getHost()) ? target.getHost() : null,
                            port,
                            "HTTP");
            AccessControlService.AccessDecision decision =
                    accessControlService.decide(accessRequest);
            if (decision.action() == AccessRule.Action.BLOCK) {
                if (connectionLogger != null) {
                    connectionLogger.log(
                            accessRequest,
                            decision,
                            ConnectionLog.ExecutionStatus.BLOCKED,
                            null);
                }
                sendError(clientOutput, 403, "Forbidden");
                return;
            }

            try {
                handleAllowedRequest(
                        clientSocket,
                        clientInput,
                        clientOutput,
                        target,
                        port,
                        lines,
                        contentLength,
                        accessRequest,
                        decision);
            } catch (IOException e) {
                if (connectionLogger != null) {
                    connectionLogger.log(
                            accessRequest,
                            decision,
                            ConnectionLog.ExecutionStatus.ERROR,
                            "HTTP upstream connection failed");
                }
                sendError(clientOutput, 502, "Bad Gateway");
            }
            return;
        }

        byte[] body = clientInput.readNBytes((int) contentLength);
        if (body.length != contentLength) {
            sendError(clientOutput, 400, "Incomplete request body");
            return;
        }

        try (Socket remoteSocket = new Socket()) {
            remoteSocket.connect(
                    new InetSocketAddress(target.getHost(), port),
                    CONNECT_TIMEOUT_MILLIS
            );
            remoteSocket.setSoTimeout(clientSocket.getSoTimeout());

            OutputStream remoteOutput = remoteSocket.getOutputStream();
            writeRequest(remoteOutput, lines, target, body);
            relayResponse(remoteSocket, clientOutput);
        } catch (IOException e) {
            sendError(clientOutput, 502, "Bad Gateway");
        }
    }

    private void handleAllowedRequest(
            Socket clientSocket,
            BufferedInputStream clientInput,
            OutputStream clientOutput,
            URI target,
            int port,
            String[] lines,
            long contentLength,
            AccessControlService.AccessRequest accessRequest,
            AccessControlService.AccessDecision decision
    ) throws IOException {
        byte[] body = clientInput.readNBytes((int) contentLength);
        if (body.length != contentLength) {
            sendError(clientOutput, 400, "Incomplete request body");
            return;
        }

        try (Socket remoteSocket = new Socket()) {
            remoteSocket.connect(
                    new InetSocketAddress(target.getHost(), port),
                    CONNECT_TIMEOUT_MILLIS);
            remoteSocket.setSoTimeout(clientSocket.getSoTimeout());
            if (connectionLogger != null) {
                connectionLogger.log(
                        accessRequest,
                        decision,
                        ConnectionLog.ExecutionStatus.CONNECTED,
                        null);
            }

            OutputStream remoteOutput = remoteSocket.getOutputStream();
            writeRequest(remoteOutput, lines, target, body);
            relayResponse(remoteSocket, clientOutput);
        } catch (IOException e) {
        if (connectionLogger != null) {
            connectionLogger.log(
                    accessRequest,
                    decision,
                    ConnectionLog.ExecutionStatus.ERROR,
                    "HTTP upstream connection failed");
        }
            sendError(clientOutput, 502, "Bad Gateway");
        }
    }

    private boolean isIpLiteral(String host) {
        if (host == null) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private URI resolveTarget(String requestTarget, String[] headers) {
        try {
            if (requestTarget.startsWith("http://")
                    || requestTarget.startsWith("https://")) {
                return new URI(requestTarget);
            }

            String host = null;
            for (String line : headers) {
                if (line.regionMatches(true, 0, "Host:", 0, 5)) {
                    host = line.substring(5).trim();
                    break;
                }
            }
            return host == null ? null : new URI("http://" + host + requestTarget);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private long getContentLength(String[] headers) {
        for (String line : headers) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                try {
                    return Long.parseLong(line.substring(15).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return 0;
    }

    private void writeRequest(
            OutputStream output,
            String[] lines,
            URI target,
            byte[] body
    ) throws IOException {
        StringBuilder request = new StringBuilder();
        String path = target.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (target.getRawQuery() != null) {
            path += "?" + target.getRawQuery();
        }
        request.append(lines[0].split(" ", 2)[0])
                .append(" ")
                .append(path)
                .append(" ")
                .append(lines[0].substring(lines[0].lastIndexOf(' ') + 1))
                .append("\r\n");

        boolean hasHost = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            if (line.regionMatches(true, 0, "Host:", 0, 5)) {
                hasHost = true;
            }
            if (isHopByHopHeader(line)) {
                continue;
            }
            request.append(line).append("\r\n");
        }
        if (!hasHost) {
            request.append("Host: ").append(target.getHost()).append("\r\n");
        }
        request.append("Connection: close\r\n\r\n");

        output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private boolean isHopByHopHeader(String line) {
        return line.regionMatches(true, 0, "Proxy-Connection:", 0, 17)
                || line.regionMatches(true, 0, "Connection:", 0, 11)
                || line.regionMatches(true, 0, "Keep-Alive:", 0, 11)
                || line.regionMatches(true, 0, "Transfer-Encoding:", 0, 18);
    }

    private boolean hasChunkedEncoding(String[] headers) {
        for (String line : headers) {
            if (line.regionMatches(true, 0, "Transfer-Encoding:", 0, 18)
                    && line.substring(18).toLowerCase().contains("chunked")) {
                return true;
            }
        }
        return false;
    }

    private void relayResponse(Socket remoteSocket, OutputStream clientOutput)
            throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = remoteSocket.getInputStream().read(buffer)) != -1) {
            clientOutput.write(buffer, 0, bytesRead);
            clientOutput.flush();
        }
    }

    private byte[] readHeaders(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            headers.write(current);
            if (headers.size() > MAX_HEADER_SIZE) {
                throw new IOException("HTTP headers are too large");
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
