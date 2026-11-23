package com.networkproxy.dto;

import com.networkproxy.entity.AdminUser;

import java.time.Instant;
import java.time.ZoneOffset;

public record AdminResponse(
        long id,
        String username,
        String displayName,
        Instant lastLoginAt
) {

    public static AdminResponse from(AdminUser user) {
        return new AdminResponse(
                user.userId(),
                user.username(),
                user.displayName(),
                user.lastLoginAt() == null
                        ? null
                        : user.lastLoginAt().toInstant(ZoneOffset.UTC));
    }
}
