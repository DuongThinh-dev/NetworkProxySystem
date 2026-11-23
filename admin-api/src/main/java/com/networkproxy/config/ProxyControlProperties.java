package com.networkproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy.control")
public record ProxyControlProperties(
        boolean enabled,
        String url,
        String token
) {

    public ProxyControlProperties {
        url = url == null || url.isBlank()
                ? "http://127.0.0.1:8082"
                : url.trim();
        token = token == null ? "" : token;
    }
}
