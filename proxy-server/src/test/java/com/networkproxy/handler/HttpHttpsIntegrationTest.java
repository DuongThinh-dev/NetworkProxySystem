package com.networkproxy.handler;

import com.networkproxy.https.ConnectRequestParser;
import com.networkproxy.https.HttpsConnectionConfig;
import com.networkproxy.https.HttpsTunnelManager;
import com.networkproxy.logging.ConnectionLog;
import com.networkproxy.logging.ConnectionLogger;
import com.networkproxy.network.DataRelay;
import com.networkproxy.security.AccessControlService;
import com.networkproxy.security.AccessRule;
import com.networkproxy.security.RuleManager;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpHttpsIntegrationTest {

    @Test
    void httpBlockReturns403AndDoesNotConnect() throws Exception {
        try (ServerSocket destination = new ServerSocket(0)) {
            destination.setSoTimeout(300);
            AccessControlService accessControl = service(rule(
                    1, "127.0.0.1", "127.0.0.1", destination.getLocalPort(), "HTTP",
                    AccessRule.Action.BLOCK));
            List<ConnectionLog> logs = new ArrayList<>();
            ConnectionLogger logger = new ConnectionLogger(logs::add);
            HttpHandler handler = new HttpHandler(accessControl, logger);

            String response = exchangeHttp(handler, "127.0.0.1",
                    destination.getLocalPort(), "GET");

            assertTrue(response.startsWith("HTTP/1.1 403"));
            assertEquals(ConnectionLog.ExecutionStatus.BLOCKED,
                    logs.get(0).executionStatus());
            boolean connectionAccepted = false;
            try {
                destination.accept();
                connectionAccepted = true;
            } catch (java.net.SocketTimeoutException ignored) {
            }
            assertEquals(false, connectionAccepted);
        }
    }

    @Test
    void httpAllowContinuesAndLogsConnected() throws Exception {
        try (ServerSocket destination = new ServerSocket(0)) {
            Thread destinationThread = new Thread(() -> {
                try (Socket remote = destination.accept()) {
                    OutputStream output = remote.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: 2\r\n"
                            + "Connection: close\r\n\r\nok")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    output.flush();
                } catch (Exception ignored) {
                }
            });
            destinationThread.start();

            AccessControlService accessControl = service(rule(
                    2, null, "127.0.0.1", destination.getLocalPort(), "HTTP",
                    AccessRule.Action.ALLOW));
            List<ConnectionLog> logs = new ArrayList<>();
            ConnectionLogger logger = new ConnectionLogger(logs::add);
            HttpHandler handler = new HttpHandler(accessControl, logger);

            String response = exchangeHttp(handler, "127.0.0.1",
                    destination.getLocalPort(), "GET");
            destinationThread.join(1000);

            assertTrue(response.startsWith("HTTP/1.1 200"));
            assertEquals(ConnectionLog.ExecutionStatus.CONNECTED,
                    logs.get(0).executionStatus());
        }
    }

    @Test
    void httpUpstreamErrorLogsError() throws Exception {
        int closedPort;
        try (ServerSocket reservation = new ServerSocket(0)) {
            closedPort = reservation.getLocalPort();
        }
        AccessControlService accessControl = service(rule(
                3, null, "127.0.0.1", closedPort, "HTTP",
                AccessRule.Action.ALLOW));
        List<ConnectionLog> logs = new ArrayList<>();
        HttpHandler handler = new HttpHandler(
                accessControl,
                new ConnectionLogger(logs::add));

        String response = exchangeHttp(handler, "127.0.0.1", closedPort, "GET");

        assertTrue(response.startsWith("HTTP/1.1 502"));
        assertEquals(ConnectionLog.ExecutionStatus.ERROR,
                logs.get(0).executionStatus());
    }

    @Test
    void httpsBlockReturns403WithoutTunnel() throws Exception {
        AccessControlService accessControl = service(rule(
                4, null, "example.com", 443, "HTTPS",
                AccessRule.Action.BLOCK));
        List<ConnectionLog> logs = new ArrayList<>();
        HttpsHandler handler = httpsHandler(accessControl, logs);

        String response = exchangeHttps(handler, "example.com", 443);

        assertTrue(response.startsWith("HTTP/1.1 403"));
        assertEquals(ConnectionLog.ExecutionStatus.BLOCKED,
                logs.get(0).executionStatus());
    }

    @Test
    void httpsAllowEstablishesTunnelAndLogsConnected() throws Exception {
        try (ServerSocket destination = new ServerSocket(0)) {
            int port = destination.getLocalPort();
            Thread destinationThread = new Thread(() -> {
                try (Socket remote = destination.accept()) {
                    remote.close();
                } catch (Exception ignored) {
                }
            });
            destinationThread.start();

            AccessControlService accessControl = service(rule(
                    7, null, "127.0.0.1", port, "HTTPS",
                    AccessRule.Action.ALLOW));
            List<ConnectionLog> logs = new ArrayList<>();
            HttpsTunnelManager tunnelManager = new HttpsTunnelManager(
                    new HttpsConnectionConfig(
                            1000, 1000, java.util.Set.of(port), true),
                    new DataRelay());
            HttpsHandler handler = new HttpsHandler(
                    new ConnectRequestParser(),
                    tunnelManager,
                    true,
                    accessControl,
                    new ConnectionLogger(logs::add));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (ServerSocket pairServer = new ServerSocket(0);
                 Socket proxySocket = new Socket("127.0.0.1",
                         pairServer.getLocalPort());
                 Socket handlerSocket = pairServer.accept()) {
                proxySocket.setSoTimeout(2000);
                handlerSocket.setSoTimeout(2000);
                Future<?> handled = executor.submit(() -> {
                    try {
                        handler.handle(handlerSocket);
                    } catch (Exception ignored) {
                    }
                });
                OutputStream output = proxySocket.getOutputStream();
                output.write(("CONNECT 127.0.0.1:" + port
                        + " HTTP/1.1\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                output.flush();

                String response = readLine(proxySocket);
                proxySocket.close();
                handlerSocket.close();
                handled.get(2, TimeUnit.SECONDS);

                assertTrue(response.startsWith("HTTP/1.1 200"));
                assertEquals(ConnectionLog.ExecutionStatus.CONNECTED,
                        logs.get(0).executionStatus());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void noMatchingRuleDefaultsToBlock() throws Exception {
        AccessControlService accessControl = service(rule(
                5, null, "other.example", 80, "HTTP",
                AccessRule.Action.ALLOW));
        List<ConnectionLog> logs = new ArrayList<>();
        HttpHandler handler = new HttpHandler(
                accessControl,
                new ConnectionLogger(logs::add));

        String response = exchangeHttp(handler, "127.0.0.1", 80, "GET");

        assertTrue(response.startsWith("HTTP/1.1 403"));
        assertEquals(AccessControlService.DEFAULT_DENY,
                logs.get(0).reasonCode());
    }

    @Test
    void multipleClientsCanUseHandlersConcurrently() throws Exception {
        AccessControlService accessControl = service(rule(
                6, null, null, null, null,
                AccessRule.Action.BLOCK));
        List<ConnectionLog> logs = java.util.Collections.synchronizedList(
                new ArrayList<>());
        HttpHandler handler = new HttpHandler(
                accessControl,
                new ConnectionLogger(logs::add));
        ExecutorService executor = Executors.newFixedThreadPool(8);

        List<Future<String>> responses = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            responses.add(executor.submit(() ->
                    exchangeHttp(handler, "127.0.0.1", 80, "GET")));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<String> response : responses) {
            assertTrue(response.get().startsWith("HTTP/1.1 403"));
        }
        assertEquals(20, logs.size());
    }

    private static HttpsHandler httpsHandler(
            AccessControlService accessControl,
            List<ConnectionLog> logs
    ) {
        HttpsTunnelManager tunnelManager = new HttpsTunnelManager(
                new HttpsConnectionConfig(
                        1000, 1000, java.util.Set.of(443), true),
                new DataRelay());
        return new HttpsHandler(
                new ConnectRequestParser(),
                tunnelManager,
                true,
                accessControl,
                new ConnectionLogger(logs::add));
    }

    private static String exchangeHttp(
            HttpHandler handler,
            String host,
            int port,
            String method
    ) throws Exception {
        try (ServerSocket pairServer = new ServerSocket(0);
             Socket proxySocket = new Socket("127.0.0.1",
                     pairServer.getLocalPort());
             Socket handlerSocket = pairServer.accept()) {
            proxySocket.setSoTimeout(2000);
            handlerSocket.setSoTimeout(2000);
            String request = method + " http://" + host + ":" + port + "/ HTTP/1.1\r\n"
                    + "Host: " + host + ":" + port + "\r\n\r\n";
            proxySocket.getOutputStream().write(
                    request.getBytes(StandardCharsets.ISO_8859_1));
            proxySocket.getOutputStream().flush();
            handler.handle(handlerSocket);
            return readLine(proxySocket);
        }
    }

    private static String exchangeHttps(
            HttpsHandler handler,
            String host,
            int port
    ) throws Exception {
        try (ServerSocket pairServer = new ServerSocket(0);
             Socket proxySocket = new Socket("127.0.0.1",
                     pairServer.getLocalPort());
             Socket handlerSocket = pairServer.accept()) {
            proxySocket.setSoTimeout(2000);
            handlerSocket.setSoTimeout(2000);
            String request = "CONNECT " + host + ":" + port
                    + " HTTP/1.1\r\n\r\n";
            proxySocket.getOutputStream().write(
                    request.getBytes(StandardCharsets.ISO_8859_1));
            proxySocket.getOutputStream().flush();
            handler.handle(handlerSocket);
            return readLine(proxySocket);
        }
    }

    private static String readLine(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.ISO_8859_1));
        return reader.readLine();
    }

    private static AccessControlService service(AccessRule... rules) throws Exception {
        return new AccessControlService(new RuleManager(() -> List.of(rules)));
    }

    private static AccessRule rule(
            long id,
            String clientIp,
            String target,
            Integer port,
            String protocol,
            AccessRule.Action action
    ) {
        return new AccessRule(id, clientIp, target, port, protocol,
                action, 10, true);
    }
}
