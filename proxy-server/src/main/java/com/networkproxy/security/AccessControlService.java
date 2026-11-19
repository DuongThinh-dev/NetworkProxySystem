package com.networkproxy.security;

import java.util.Locale;
import java.util.Objects;

public final class AccessControlService {

    public static final String RULE_MATCH = "RULE_MATCH";
    public static final String DEFAULT_DENY = "DEFAULT_DENY";

    private final RuleManager ruleManager;

    public AccessControlService(RuleManager ruleManager) {
        this.ruleManager = Objects.requireNonNull(ruleManager, "Rule manager is required");
    }

    public AccessDecision decide(AccessRequest request) {
        Objects.requireNonNull(request, "Access request is required");

        for (AccessRule rule : ruleManager.getRules()) {
            if (matches(rule, request)) {
                return new AccessDecision(rule.action(), RULE_MATCH, rule);
            }
        }
        return new AccessDecision(AccessRule.Action.BLOCK, DEFAULT_DENY, null);
    }

    private static boolean matches(AccessRule rule, AccessRequest request) {
        return matchesOptional(rule.clientIp(), request.clientIp())
                && matchesTarget(rule.target(), request.destinationHost(),
                        request.destinationIp())
                && (rule.targetPort() == null
                || rule.targetPort().equals(request.destinationPort()))
                && (rule.protocol() == null
                || rule.protocol().equals(request.protocol()));
    }

    private static boolean matchesOptional(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }

    private static boolean matchesTarget(String target, String host, String destinationIp) {
        return target == null || target.equals(host) || target.equals(destinationIp);
    }

    public record AccessRequest(
            String clientIp,
            String destinationHost,
            String destinationIp,
            int destinationPort,
            String protocol
    ) {
        public AccessRequest {
            clientIp = normalizeRequired(clientIp, "Client IP");
            destinationHost = normalizeHost(destinationHost);
            destinationIp = normalizeOptional(destinationIp);
            protocol = normalizeProtocol(protocol);
            if (destinationPort < 1 || destinationPort > 65535) {
                throw new IllegalArgumentException(
                        "Destination port must be between 1 and 65535");
            }
        }

        private static String normalizeRequired(String value, String field) {
            String normalized = normalizeOptional(value);
            if (normalized == null) {
                throw new IllegalArgumentException(field + " is required");
            }
            return normalized;
        }

        private static String normalizeOptional(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static String normalizeHost(String value) {
            String normalized = normalizeRequired(value, "Destination host")
                    .toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private static String normalizeProtocol(String value) {
            String normalized = normalizeRequired(value, "Protocol")
                    .toUpperCase(Locale.ROOT);
            if (!normalized.equals("HTTP") && !normalized.equals("HTTPS")
                    && !normalized.equals("SSH")) {
                throw new IllegalArgumentException("Unsupported protocol: " + normalized);
            }
            return normalized;
        }
    }

    public record AccessDecision(
            AccessRule.Action action,
            String reason,
            AccessRule matchedRule
    ) {
        public AccessDecision {
            Objects.requireNonNull(action, "Decision action is required");
            Objects.requireNonNull(reason, "Decision reason is required");
        }
    }
}
