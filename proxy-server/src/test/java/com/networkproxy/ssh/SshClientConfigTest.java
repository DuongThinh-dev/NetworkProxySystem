package com.networkproxy.ssh;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SshClientConfigTest {

    @Test
    void requiresAuthenticationConfiguration() {
        assertThrows(IllegalArgumentException.class, () ->
                new SshClientConfig(
                        "192.168.1.20",
                        22,
                        "admin",
                        1000,
                        Path.of("/tmp/known_hosts"),
                        null,
                        null));
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () ->
                new SshClientConfig(
                        "192.168.1.20",
                        0,
                        "admin",
                        1000,
                        Path.of("/tmp/known_hosts"),
                        Path.of("/tmp/id_ed25519")));
    }
}
