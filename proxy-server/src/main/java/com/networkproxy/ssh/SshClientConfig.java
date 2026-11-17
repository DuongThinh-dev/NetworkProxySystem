package com.networkproxy.ssh;

import java.nio.file.Path;

public record SshClientConfig(
        String host,
        int port,
        String username,
        int connectTimeoutMillis,
        Path knownHostsPath,
        Path privateKeyPath,
        String password
) {
    public SshClientConfig {
        if (host == null || host.isBlank()
                || username == null || username.isBlank()
                || knownHostsPath == null) {
            throw new IllegalArgumentException("Invalid SSH configuration");
        }
        if (port < 1 || port > 65535 || connectTimeoutMillis < 1) {
            throw new IllegalArgumentException("Invalid SSH port or timeout");
        }
        if (privateKeyPath == null && (password == null || password.isBlank())) {
            throw new IllegalArgumentException("SSH authentication is required");
        }
    }

    public SshClientConfig(
            String host,
            int port,
            String username,
            int connectTimeoutMillis,
            Path knownHostsPath,
            Path privateKeyPath
    ) {
        this(host, port, username, connectTimeoutMillis, knownHostsPath,
                privateKeyPath, null);
    }
}
