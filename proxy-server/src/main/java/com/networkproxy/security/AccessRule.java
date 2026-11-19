package com.networkproxy.security;

import java.util.Locale;

public record AccessRule(
        long id,
        String clientIp,
        String target,
        Integer targetPort,
        String protocol,
        Action action,
        int priority,
        boolean enabled
) {

    public AccessRule {
        clientIp = normalizeOptional(clientIp);
        target = normalizeTarget(target);
        protocol = protocol == null || protocol.isBlank()
                ? null
                : protocol.trim().toUpperCase(Locale.ROOT);

        if (targetPort != null && (targetPort < 1 || targetPort > 65535)) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        if (clientIp != null && clientIp.length() > 45) {
            throw new IllegalArgumentException("Client IP exceeds 45 characters");
        }
        if (target != null && target.length() > 253) {
            throw new IllegalArgumentException("Target exceeds 253 characters");
        }
        if (protocol != null && !protocol.equals("HTTP")
                && !protocol.equals("HTTPS") && !protocol.equals("SSH")) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        if (action == null) {
            throw new IllegalArgumentException("Rule action is required");
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeTarget(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public enum Action {
        ALLOW,
        BLOCK
    }
}
