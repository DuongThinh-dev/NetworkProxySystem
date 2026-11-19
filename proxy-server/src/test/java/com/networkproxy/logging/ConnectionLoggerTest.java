package com.networkproxy.logging;

import com.networkproxy.security.AccessControlService;
import com.networkproxy.security.AccessRule;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionLoggerTest {

    private static final AccessControlService.AccessRequest REQUEST =
            new AccessControlService.AccessRequest(
                    "10.0.0.5", "Example.COM.", "93.184.216.34", 443, "HTTPS");

    @Test
    void logsAllowDecisionWithCompleteMetadata() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessRule rule = rule(101, AccessRule.Action.ALLOW);
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        rule);
        LocalDateTime before = LocalDateTime.now(java.time.ZoneOffset.UTC);

        assertTrue(logger.log(REQUEST, decision));

        ConnectionLog log = captured.get();
        assertNotNull(log);
        assertEquals("10.0.0.5", log.clientIp());
        assertEquals("example.com", log.target());
        assertEquals(443, log.targetPort());
        assertEquals("HTTPS", log.protocol());
        assertEquals(AccessRule.Action.ALLOW, log.decision());
        assertEquals(101L, log.matchedRuleId());
        assertEquals(AccessControlService.RULE_MATCH, log.reasonCode());
        assertEquals(ConnectionLog.ExecutionStatus.CONNECTED, log.executionStatus());
        assertTrue(!log.occurredAtUtc().isBefore(before));
    }

    @Test
    void logsBlockDecisionAndExecutionStatus() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.BLOCK,
                        AccessControlService.DEFAULT_DENY,
                        null);

        assertTrue(logger.log(REQUEST, decision));

        ConnectionLog log = captured.get();
        assertEquals(AccessRule.Action.BLOCK, log.decision());
        assertEquals(ConnectionLog.ExecutionStatus.BLOCKED, log.executionStatus());
        assertNull(log.matchedRuleId());
        assertEquals(AccessControlService.DEFAULT_DENY, log.reasonCode());
    }

    @Test
    void supportsCustomExecutionStatusAndErrorMessage() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        rule(102, AccessRule.Action.ALLOW));

        assertTrue(logger.log(
                REQUEST,
                decision,
                ConnectionLog.ExecutionStatus.ERROR,
                "upstream connection failed"));

        assertEquals(ConnectionLog.ExecutionStatus.ERROR,
                captured.get().executionStatus());
        assertEquals("upstream connection failed", captured.get().errorMessage());
    }

    @Test
    void supportsMissingDestinationIp() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessControlService.AccessRequest request =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 80, "HTTP");
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        rule(103, AccessRule.Action.ALLOW));

        assertTrue(logger.log(request, decision));
        assertEquals("example.com", captured.get().target());
    }

    @Test
    void repositoryFailureIsReportedWithoutThrowing() {
        ConnectionLogger logger = new ConnectionLogger(log -> {
            throw new SQLException("database unavailable", "08001");
        });
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        null);

        assertFalse(logger.log(REQUEST, decision));
        assertEquals(AccessRule.Action.ALLOW, decision.action());
        assertEquals(AccessControlService.RULE_MATCH, decision.reason());
    }

    @Test
    void logsSshConnectedWithMatchedRule() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessControlService.AccessRequest request =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessRule rule = rule(104, AccessRule.Action.ALLOW);
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        rule);

        assertTrue(logger.log(request, decision));

        ConnectionLog log = captured.get();
        assertEquals("10.0.0.5", log.clientIp());
        assertEquals("example.com", log.target());
        assertEquals(22, log.targetPort());
        assertEquals("SSH", log.protocol());
        assertEquals(AccessRule.Action.ALLOW, log.decision());
        assertEquals(104L, log.matchedRuleId());
        assertEquals(ConnectionLog.ExecutionStatus.CONNECTED, log.executionStatus());
    }

    @Test
    void logsSshBlockedWithoutMatchedRule() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessControlService.AccessRequest request =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.BLOCK,
                        AccessControlService.DEFAULT_DENY,
                        null);

        assertTrue(logger.log(request, decision));

        ConnectionLog log = captured.get();
        assertEquals("SSH", log.protocol());
        assertEquals(22, log.targetPort());
        assertEquals(AccessRule.Action.BLOCK, log.decision());
        assertEquals(ConnectionLog.ExecutionStatus.BLOCKED, log.executionStatus());
        assertEquals(AccessControlService.DEFAULT_DENY, log.reasonCode());
        assertNull(log.matchedRuleId());
    }

    @Test
    void logsSshConnectionErrorAndTimeout() {
        AtomicReference<ConnectionLog> captured = new AtomicReference<>();
        ConnectionLogger logger = new ConnectionLogger(log -> captured.set(log));
        AccessControlService.AccessRequest request =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        rule(105, AccessRule.Action.ALLOW));

        assertTrue(logger.log(
                request,
                decision,
                ConnectionLog.ExecutionStatus.ERROR,
                "SSH gateway timeout"));

        ConnectionLog log = captured.get();
        assertEquals("SSH", log.protocol());
        assertEquals(ConnectionLog.ExecutionStatus.ERROR, log.executionStatus());
        assertEquals("SSH gateway timeout", log.errorMessage());
        assertEquals(105L, log.matchedRuleId());
    }

    @Test
    void sshLoggingFailureDoesNotChangeDecision() {
        ConnectionLogger logger = new ConnectionLogger(log -> {
            throw new SQLException("database unavailable", "08001");
        });
        AccessControlService.AccessRequest request =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessControlService.AccessDecision decision =
                new AccessControlService.AccessDecision(
                        AccessRule.Action.ALLOW,
                        AccessControlService.RULE_MATCH,
                        rule(106, AccessRule.Action.ALLOW));

        assertFalse(logger.log(
                request,
                decision,
                ConnectionLog.ExecutionStatus.CONNECTED,
                null));
        assertEquals(AccessRule.Action.ALLOW, decision.action());
        assertEquals(AccessControlService.RULE_MATCH, decision.reason());
    }

    private static AccessRule rule(long id, AccessRule.Action action) {
        return new AccessRule(id, null, null, null, null,
                action, 10, true);
    }
}
