## Configuration

The proxy reads `proxy-server/src/main/resources/application.properties`.
Operational defaults are:

```properties
proxy.port=8080
proxy.max-threads=50
proxy.client-timeout=60000
proxy.upstream.connect-timeout=10000
https.enabled=true
https.allow-private-destinations=false
https.tunnel-timeout=60000
ssh.enabled=true
ssh.default-port=22
ssh.connect-timeout=10000
ssh.known-hosts-path=${user.home}/.ssh/known_hosts
```

Do not put passwords, tokens or private-key contents in this file.

## HTTPS proxy usage

Configure an HTTP client to use the proxy at `host:8080`. HTTPS requests are
handled with HTTP CONNECT. The proxy only tunnels encrypted traffic; it does
not decrypt TLS or replace certificate validation.

Private, loopback and link-local destinations are denied by default. Enable
`https.allow-private-destinations` only for a controlled and authorized LAN
test environment.

## SSH management usage

SSH connections are created by the outbound `SshConnectionManager` service.
Use a private key path or a password supplied at runtime and provide a
`known_hosts` file containing the expected server key. Unknown server keys are
rejected. The service does not expose arbitrary remote shell execution.

Example configuration object:

```java
SshClientConfig config = new SshClientConfig(
        "192.168.1.20",
        22,
        "operator",
        10_000,
        Path.of(System.getProperty("user.home"), ".ssh", "known_hosts"),
        Path.of(System.getProperty("user.home"), ".ssh", "id_ed25519"));
```

## Running

From `proxy-server`:

```text
mvn clean test
mvn clean package
mvn exec:java
```