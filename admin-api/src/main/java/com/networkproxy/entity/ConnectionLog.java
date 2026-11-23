package com.networkproxy.entity;

import java.time.LocalDateTime;

public record ConnectionLog(
        long id,
        LocalDateTime occurredAt,
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
}
