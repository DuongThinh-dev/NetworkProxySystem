package com.networkproxy.config;

public class ProxyConfig {

    private final int port;
    private final int maxThreads;
    private final int clientTimeout;

    public ProxyConfig(int port, int maxThreads, int clientTimeout) {

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port khong hop le");
        }

        if (maxThreads < 1) {
            throw new IllegalArgumentException("So luong thread phai lon hon 0");
        }

        this.port = port;
        this.maxThreads = maxThreads;
        this.clientTimeout = clientTimeout;
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
}