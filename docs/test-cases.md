## Automated tests

Run from `proxy-server`:

```text
mvn clean test
```

Current tests cover:

- Valid and invalid HTTP CONNECT request parsing.
- Invalid and disallowed destination ports.
- SSH configuration validation, including missing authentication and invalid
  ports.

Network integration tests should use local temporary TCP/SSH servers. They
must not depend on public websites or connect to machines without
authorization.

## Manual scenarios

### HTTPS

1. Start a local TLS server or use an authorized HTTPS destination.
2. Configure the proxy client to use HTTP proxy `127.0.0.1:8080`.
3. Request an HTTPS URL.
4. Verify that CONNECT receives `200 Connection Established`.
5. Verify the TLS certificate is validated by the client, not by a
   trust-all proxy implementation.
6. Repeat with a refused port, invalid host and disallowed private address.

### SSH

1. Ensure the target SSH server is authorized.
2. Add its host key to the configured `known_hosts` file.
3. Create an `SshClientConfig` with the target, username and private key path.
4. Connect and check `SshConnection.isConnected()`.
5. Close the connection and verify the session is removed.
6. Repeat with an unknown host key and invalid credentials.