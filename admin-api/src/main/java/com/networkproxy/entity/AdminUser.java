package com.networkproxy.entity;

import java.time.LocalDateTime;

public record AdminUser(
        long userId,
        String username,
        String passwordHash,
        String displayName,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
}
