package com.networkproxy.dto;

public record ProxyStats(
        boolean online,
        int activeClients,
        int loadedRules,
        long uptimeSeconds,
        String version,
        String message
) {

    public static ProxyStats offline(String message) {
        return new ProxyStats(false, 0, 0, 0, null, message);
    }
}
