package com.networkproxy.https;

import java.util.Set;

public record HttpsConnectionConfig(
        int connectTimeoutMillis,
        int tunnelTimeoutMillis,
        Set<Integer> allowedPorts,
        boolean allowPrivateDestinations
) {
    public HttpsConnectionConfig {
        if (connectTimeoutMillis < 1 || tunnelTimeoutMillis < 1
                || allowedPorts == null || allowedPorts.isEmpty()) {
            throw new IllegalArgumentException("Invalid HTTPS configuration");
        }
        if (allowedPorts.stream().anyMatch(port -> port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Invalid HTTPS port configuration");
        }
        allowedPorts = Set.copyOf(allowedPorts);
    }
}
