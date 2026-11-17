## Proxy connection architecture

The `proxy-server` module is a Java 17 TCP proxy. `ProxyServer` accepts client
sockets, `ConnectionManager` assigns them to the thread pool, and
`ClientHandler` routes the request to a protocol handler.

Normal HTTP requests use `HttpHandler`. HTTP `CONNECT` requests use
`HttpsHandler`, which establishes a TCP connection to the requested
destination and reuses `DataRelay` for bidirectional forwarding.

### HTTPS CONNECT

The proxy does not terminate TLS. After returning `200 Connection
Established`, it forwards encrypted bytes without inspecting or changing them.
The client and destination server perform the TLS handshake and certificate
verification directly.

Destination ports are restricted to 443 by default. Loopback, link-local,
multicast and private addresses are rejected unless
`https.allow-private-destinations=true` is explicitly configured for a
controlled LAN environment.

### SSH

SSH is an outbound management client, not an inbound SSH server. The
`SshConnectionManager` uses Apache MINA SSHD and requires a configured
`known_hosts` file. It supports password or private-key authentication without
executing remote commands. Unknown host keys are rejected and credentials are
never written to logs.

An incoming raw SSH handshake is not routed automatically because it does not
contain a safe upstream target. Any future SSH tunnel must use an explicit,
authorized routing contract.