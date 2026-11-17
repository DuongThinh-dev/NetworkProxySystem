package com.networkproxy.ssh;

public class SshConnection implements AutoCloseable {

    private final long sessionId;
    private final SshSessionManager sessionManager;

    SshConnection(long sessionId, SshSessionManager sessionManager) {
        this.sessionId = sessionId;
        this.sessionManager = sessionManager;
    }

    public long getSessionId() {
        return sessionId;
    }

    public boolean isConnected() {
        return sessionManager.isConnected(sessionId);
    }

    @Override
    public void close() throws SshException {
        sessionManager.close(sessionId);
    }
}
