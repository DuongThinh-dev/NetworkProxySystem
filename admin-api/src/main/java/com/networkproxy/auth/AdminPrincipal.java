package com.networkproxy.auth;

import java.io.Serializable;

public record AdminPrincipal(
        long userId,
        String username,
        String displayName
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
