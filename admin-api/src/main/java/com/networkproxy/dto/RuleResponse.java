package com.networkproxy.dto;

import com.networkproxy.entity.AccessRule;

import java.time.Instant;
import java.time.ZoneOffset;

public record RuleResponse(
        long id,
        String clientIp,
        String target,
        Integer targetPort,
        String protocol,
        String action,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static RuleResponse from(AccessRule rule) {
        return new RuleResponse(
                rule.id(),
                rule.clientIp(),
                rule.target(),
                rule.targetPort(),
                rule.protocol(),
                rule.action().name(),
                rule.priority(),
                rule.enabled(),
                toInstant(rule.createdAt()),
                toInstant(rule.updatedAt()));
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
