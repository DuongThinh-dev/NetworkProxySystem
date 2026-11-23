package com.networkproxy.dto;

import com.networkproxy.entity.AuditLog;

import java.time.Instant;
import java.time.ZoneOffset;

public record AuditLogResponse(
        long id,
        Instant createdAt,
        Long userId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String details
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.id(),
                log.createdAt() == null
                        ? null
                        : log.createdAt().toInstant(ZoneOffset.UTC),
                log.userId(),
                log.username(),
                log.action(),
                log.entityType(),
                log.entityId(),
                log.details());
    }
}
