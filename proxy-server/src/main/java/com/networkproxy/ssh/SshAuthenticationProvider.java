package com.networkproxy.ssh;

import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

import java.nio.file.Files;
import java.util.List;

public class SshAuthenticationProvider {

    public void configure(ClientSession session, SshClientConfig config)
            throws SshException {
        try {
            if (config.privateKeyPath() != null) {
                if (!Files.isRegularFile(config.privateKeyPath())) {
                    throw new SshException("SSH private key file does not exist");
                }
                session.setKeyIdentityProvider(new FileKeyPairProvider(
                        List.of(config.privateKeyPath())));
            }
            if (config.password() != null && !config.password().isBlank()) {
                session.addPasswordIdentity(config.password());
            }
        } catch (RuntimeException e) {
            throw new SshException("Unable to configure SSH authentication", e);
        }
    }
}
