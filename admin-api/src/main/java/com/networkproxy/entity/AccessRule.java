package com.networkproxy.entity;

import java.time.LocalDateTime;

public record AccessRule(
        long id,
        String clientIp,
        String target,
        Integer targetPort,
        String protocol,
        Action action,
        int priority,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public enum Action {
        ALLOW,
        BLOCK
    }
}
