package com.networkproxy.dto;

import com.networkproxy.entity.ConnectionLog;

import java.time.Instant;
import java.time.ZoneOffset;

public record LogResponse(
        long id,
        Instant occurredAt,
        String clientIp,
        String target,
        int targetPort,
        String protocol,
        String decision,
        Long matchedRuleId,
        String reasonCode,
        String executionStatus,
        String errorMessage
) {

    public static LogResponse from(ConnectionLog log) {
        return new LogResponse(
                log.id(),
                log.occurredAt() == null
                        ? null
                        : log.occurredAt().toInstant(ZoneOffset.UTC),
                log.clientIp(),
                log.target(),
                log.targetPort(),
                log.protocol(),
                log.decision(),
                log.matchedRuleId(),
                log.reasonCode(),
                log.executionStatus(),
                log.errorMessage());
    }
}
