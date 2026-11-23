package com.networkproxy.entity;

import java.time.LocalDateTime;

public record AuditLog(
        long id,
        LocalDateTime createdAt,
        Long userId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String details
) {
}
