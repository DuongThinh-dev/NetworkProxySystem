package com.networkproxy.config;

import java.nio.file.Path;

public class ProxyConfig {

    private final int port;
    private final int maxThreads;
    private final int clientTimeout;
    private final int upstreamConnectTimeout;
    private final boolean httpsEnabled;
    private final boolean httpsAllowPrivateDestinations;
    private final int httpsTunnelTimeout;
    private final boolean sshEnabled;
    private final int sshDefaultPort;
    private final int sshConnectTimeout;
    private final Path sshKnownHostsPath;

    public ProxyConfig(int port, int maxThreads, int clientTimeout) {
        this(port, maxThreads, clientTimeout, 10_000, true, false,
                60_000, true, 22, 10_000,
                Path.of(System.getProperty("user.home"), ".ssh", "known_hosts"));
    }

    public ProxyConfig(
            int port,
            int maxThreads,
            int clientTimeout,
            int upstreamConnectTimeout,
            boolean httpsEnabled,
            boolean httpsAllowPrivateDestinations,
            int httpsTunnelTimeout,
            boolean sshEnabled,
            int sshDefaultPort,
            int sshConnectTimeout,
            Path sshKnownHostsPath
    ) {

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port khong hop le");
        }

        if (maxThreads < 1) {
            throw new IllegalArgumentException("So luong thread phai lon hon 0");
        }
        if (clientTimeout < 1 || upstreamConnectTimeout < 1
                || httpsTunnelTimeout < 1 || sshConnectTimeout < 1) {
            throw new IllegalArgumentException("Timeout phai lon hon 0");
        }
        validatePort(sshDefaultPort);
        if (sshKnownHostsPath == null) {
            throw new IllegalArgumentException("Known hosts path khong duoc rong");
        }

        this.port = port;
        this.maxThreads = maxThreads;
        this.clientTimeout = clientTimeout;
        this.upstreamConnectTimeout = upstreamConnectTimeout;
        this.httpsEnabled = httpsEnabled;
        this.httpsAllowPrivateDestinations = httpsAllowPrivateDestinations;
        this.httpsTunnelTimeout = httpsTunnelTimeout;
        this.sshEnabled = sshEnabled;
        this.sshDefaultPort = sshDefaultPort;
        this.sshConnectTimeout = sshConnectTimeout;
        this.sshKnownHostsPath = sshKnownHostsPath;
    }

    private static void validatePort(int value) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("Port khong hop le");
        }
    }

    public int getPort() {
        return port;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getClientTimeout() {
        return clientTimeout;
    }

    public int getUpstreamConnectTimeout() {
        return upstreamConnectTimeout;
    }

    public boolean isHttpsEnabled() {
        return httpsEnabled;
    }

    public boolean isHttpsAllowPrivateDestinations() {
        return httpsAllowPrivateDestinations;
    }

    public int getHttpsTunnelTimeout() {
        return httpsTunnelTimeout;
    }

    public boolean isSshEnabled() {
        return sshEnabled;
    }

    public int getSshDefaultPort() {
        return sshDefaultPort;
    }

    public int getSshConnectTimeout() {
        return sshConnectTimeout;
    }

    public Path getSshKnownHostsPath() {
        return sshKnownHostsPath;
    }
}