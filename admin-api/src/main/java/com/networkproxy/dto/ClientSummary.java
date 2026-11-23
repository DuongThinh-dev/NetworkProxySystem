package com.networkproxy.dto;

import java.time.Instant;

public record ClientSummary(
        String clientIp,
        long totalRequests,
        long allowed,
        long blocked,
        long errors,
        Instant lastSeen
) {
}
