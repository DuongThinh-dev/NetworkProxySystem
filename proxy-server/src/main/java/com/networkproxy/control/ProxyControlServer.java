package com.networkproxy.control;

import com.networkproxy.core.ConnectionManager;
import com.networkproxy.security.RuleManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Control API tối giản dùng JDK HttpServer.
 *
 * Endpoint:
 *   GET  /admin/health
 *   GET  /admin/stats
 *   POST /admin/reload-rules
 *
 * Mọi request phải gửi header X-Admin-Token trùng với cấu hình.
 */
public final class ProxyControlServer implements AutoCloseable {

    private static final String VERSION = "1.0";
    private static final String TOKEN_HEADER = "X-Admin-Token";

    private final HttpServer server;
    private final ExecutorService executor;
    private final RuleManager ruleManager;
    private final ConnectionManager connectionManager;
    private final String token;
    private final Instant startedAt = Instant.now();

    public ProxyControlServer(
            String host,
            int port,
            String token,
            RuleManager ruleManager,
            ConnectionManager connectionManager
    ) throws IOException {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Proxy control token là bắt buộc");
        }
        this.token = token;
        this.ruleManager = ruleManager;
        this.connectionManager = connectionManager;

        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "proxy-control-api");
            thread.setDaemon(true);
            return thread;
        });
        this.server.setExecutor(executor);
        this.server.createContext("/admin/health", this::handleHealth);
        this.server.createContext("/admin/stats", this::handleStats);
        this.server.createContext("/admin/reload-rules", this::handleReload);
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!ensureAuthorized(exchange) || !ensureMethod(exchange, "GET")) {
            return;
        }
        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (!ensureAuthorized(exchange) || !ensureMethod(exchange, "GET")) {
            return;
        }

        long uptimeSeconds = Math.max(0,
                Instant.now().getEpochSecond() - startedAt.getEpochSecond());
        String body = "{\"status\":\"ok\""
                + ",\"activeClients\":" + connectionManager.getActiveClientCount()
                + ",\"loadedRules\":" + ruleManager.getRules().size()
                + ",\"uptimeSeconds\":" + uptimeSeconds
                + ",\"version\":\"" + VERSION + "\"}";
        sendJson(exchange, 200, body);
    }

    private void handleReload(HttpExchange exchange) throws IOException {
        if (!ensureAuthorized(exchange) || !ensureMethod(exchange, "POST")) {
            return;
        }

        try {
            ruleManager.reload();
            sendJson(exchange, 200,
                    "{\"status\":\"ok\",\"rules\":"
                            + ruleManager.getRules().size() + "}");
        } catch (SQLException exception) {
            sendJson(exchange, 500,
                    "{\"status\":\"error\","
                            + "\"message\":\"Cannot reload rules from database\"}");
        } catch (RuntimeException exception) {
            sendJson(exchange, 500,
                    "{\"status\":\"error\","
                            + "\"message\":\"Cannot reload rules\"}");
        }
    }

    private boolean ensureAuthorized(HttpExchange exchange) throws IOException {
        String provided = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        if (provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        sendJson(exchange, 401,
                "{\"status\":\"error\",\"message\":\"Unauthorized\"}");
        return false;
    }

    private boolean ensureMethod(HttpExchange exchange, String expected)
            throws IOException {
        if (expected.equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }

        exchange.getResponseHeaders().set("Allow", expected);
        sendJson(exchange, 405,
                "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
        return false;
    }

    private void sendJson(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
