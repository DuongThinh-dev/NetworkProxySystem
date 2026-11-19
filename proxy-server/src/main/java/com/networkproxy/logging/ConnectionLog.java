package com.networkproxy.logging;

import com.networkproxy.security.AccessRule;

import java.time.LocalDateTime;
import java.util.Locale;

public record ConnectionLog(
        LocalDateTime occurredAtUtc,
        String clientIp,
        String target,
        int targetPort,
        String protocol,
        AccessRule.Action decision,
        Long matchedRuleId,
        String reasonCode,
        ExecutionStatus executionStatus,
        String errorMessage
) {

    public ConnectionLog {
        if (occurredAtUtc == null || clientIp == null || clientIp.isBlank()
                || target == null || target.isBlank() || decision == null
                || reasonCode == null || reasonCode.isBlank()
                || executionStatus == null) {
            throw new IllegalArgumentException("Required connection log fields are missing");
        }
        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        protocol = protocol == null ? null : protocol.trim().toUpperCase(Locale.ROOT);
        if (!"HTTP".equals(protocol) && !"HTTPS".equals(protocol)
                && !"SSH".equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        if (clientIp.length() > 45 || target.length() > 253
                || reasonCode.length() > 64) {
            throw new IllegalArgumentException("Connection log field exceeds database capacity");
        }
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        if (matchedRuleId != null && matchedRuleId < 1) {
            throw new IllegalArgumentException("Matched rule id must be positive");
        }
        if (executionStatus == ExecutionStatus.BLOCKED
                && decision != AccessRule.Action.BLOCK) {
            throw new IllegalArgumentException("A blocked execution must have a BLOCK decision");
        }
    }

    public enum ExecutionStatus {
        CONNECTED,
        BLOCKED,
        ERROR
    }
}
