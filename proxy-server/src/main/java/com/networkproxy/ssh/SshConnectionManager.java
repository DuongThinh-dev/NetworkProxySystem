package com.networkproxy.ssh;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

public class SshConnectionManager implements AutoCloseable {

    private final SshClient client;
    private final SshSessionManager sessionManager;
    private final SshAuthenticationProvider authenticationProvider;
    private final Set<String> allowedHosts;

    public SshConnectionManager(SshClientConfig config) throws SshException {
        this(config, Set.of(config.host()));
    }

    public SshConnectionManager(
            SshClientConfig config,
            Set<String> allowedHosts
    ) throws SshException {
        if (!Files.isRegularFile(config.knownHostsPath())) {
            throw new SshException("Known hosts file does not exist");
        }
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new SshException("SSH destination allowlist is empty");
        }
        this.client = SshClient.setUpDefaultClient();
        this.client.setServerKeyVerifier(new KnownHostsServerKeyVerifier(
                RejectAllServerKeyVerifier.INSTANCE,
                config.knownHostsPath()));
        this.sessionManager = new SshSessionManager();
        this.authenticationProvider = new SshAuthenticationProvider();
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.client.start();
    }

    public SshConnection connect(SshClientConfig config) throws SshException {
        if (!allowedHosts.contains(config.host())) {
            throw new SshException("SSH destination is not authorized");
        }
        try {
            ClientSession session = client.connect(
                    config.username(), config.host(), config.port())
                    .verify(config.connectTimeoutMillis())
                    .getSession();
            authenticationProvider.configure(session, config);
            session.auth().verify(config.connectTimeoutMillis());
            long sessionId = sessionManager.register(session);
            return new SshConnection(sessionId, sessionManager);
        } catch (IOException | RuntimeException e) {
            if (isAuthenticationFailure(e)) {
                throw new SshAuthenticationException(
                        "SSH authentication failed", e);
            }
            if (e.getMessage() != null
                    && e.getMessage().toLowerCase().contains("server key")) {
                throw new SshHostKeyVerificationException(
                        "SSH host key verification failed", e);
            }
            throw new SshException("Unable to establish SSH connection", e);
        }
    }

    private boolean isAuthenticationFailure(Throwable error) {
        String message = error.getMessage();
        return message != null && message.toLowerCase().contains("auth");
    }

    @Override
    public void close() {
        sessionManager.close();
        client.stop();
    }
}
