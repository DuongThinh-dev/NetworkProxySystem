package com.networkproxy.logging;

import com.networkproxy.repository.LogRepository;
import com.networkproxy.security.AccessControlService;
import com.networkproxy.security.AccessRule;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class ConnectionLogger {

    private final ConnectionLogWriter logWriter;

    public ConnectionLogger(LogRepository logRepository) {
        this((ConnectionLogWriter) Objects.requireNonNull(
                logRepository, "Log repository is required"));
    }

    public ConnectionLogger(ConnectionLogWriter logWriter) {
        this.logWriter = Objects.requireNonNull(logWriter, "Log writer is required");
    }

    public boolean log(
            AccessControlService.AccessRequest request,
            AccessControlService.AccessDecision decision
    ) {
        ConnectionLog.ExecutionStatus status =
                decision.action() == AccessRule.Action.BLOCK
                        ? ConnectionLog.ExecutionStatus.BLOCKED
                        : ConnectionLog.ExecutionStatus.CONNECTED;
        return log(request, decision, status, null);
    }

    public boolean log(
            AccessControlService.AccessRequest request,
            AccessControlService.AccessDecision decision,
            ConnectionLog.ExecutionStatus executionStatus,
            String errorMessage
    ) {
        Objects.requireNonNull(request, "Access request is required");
        Objects.requireNonNull(decision, "Access decision is required");
        Objects.requireNonNull(executionStatus, "Execution status is required");

        Long matchedRuleId = decision.matchedRule() == null
                ? null
                : decision.matchedRule().id();

        try {
            ConnectionLog log = new ConnectionLog(
                    LocalDateTime.now(ZoneOffset.UTC),
                    request.clientIp(),
                    normalizeTarget(request.destinationHost(), request.destinationIp()),
                    request.destinationPort(),
                    request.protocol(),
                    decision.action(),
                    matchedRuleId,
                    decision.reason(),
                    executionStatus,
                    errorMessage
            );
            logWriter.insert(log);
            return true;
        } catch (SQLException exception) {
            System.err.println("ConnectionLogger: failed to persist connection log"
                    + " (SQLState=" + safeSqlState(exception.getSQLState()) + ")");
            return false;
        } catch (RuntimeException exception) {
            System.err.println("ConnectionLogger: failed to create connection log"
                    + " (" + exception.getClass().getSimpleName() + ")");
            return false;
        }
    }

    private static String normalizeTarget(String destinationHost, String destinationIp) {
        if (destinationHost != null && !destinationHost.isBlank()) {
            return destinationHost.trim().toLowerCase(java.util.Locale.ROOT);
        }
        return destinationIp == null ? null : destinationIp.trim();
    }

    private static String safeSqlState(String sqlState) {
        return sqlState == null || sqlState.isBlank() ? "unknown" : sqlState;
    }
}
