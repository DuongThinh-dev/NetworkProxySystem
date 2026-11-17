package com.networkproxy.ssh;

import org.apache.sshd.client.session.ClientSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SshSessionManager implements AutoCloseable {

    private final AtomicLong idGenerator = new AtomicLong();
    private final Map<Long, ClientSession> sessions = new ConcurrentHashMap<>();

    public long register(ClientSession session) {
        long id = idGenerator.incrementAndGet();
        sessions.put(id, session);
        return id;
    }

    public boolean isConnected(long sessionId) {
        ClientSession session = sessions.get(sessionId);
        return session != null && session.isOpen() && session.isAuthenticated();
    }

    public void close(long sessionId) throws SshException {
        ClientSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (IOException e) {
            throw new SshException("Unable to close SSH session", e);
        }
    }

    @Override
    public void close() {
        sessions.keySet().forEach(sessionId -> {
            try {
                close(sessionId);
            } catch (SshException e) {
                System.err.println("Unable to close SSH session: " + e.getMessage());
            }
        });
    }
}
