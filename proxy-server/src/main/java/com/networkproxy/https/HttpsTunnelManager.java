package com.networkproxy.https;

import com.networkproxy.network.DataRelay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class HttpsTunnelManager {

    private final HttpsConnectionConfig config;
    private final DataRelay dataRelay;

    public HttpsTunnelManager(HttpsConnectionConfig config, DataRelay dataRelay) {
        this.config = config;
        this.dataRelay = dataRelay;
    }

    public Socket connect(ConnectRequest request) throws IOException {
        if (!config.allowedPorts().contains(request.port())) {
            throw new HttpsException(403, "Destination port is not allowed");
        }

        InetAddress[] addresses = InetAddress.getAllByName(request.host());
        if (addresses.length == 0) {
            throw new HttpsException(502, "Destination could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (!config.allowPrivateDestinations() && isRestricted(address)) {
                throw new HttpsException(403, "Destination is not allowed");
            }
        }

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(addresses[0], request.port()),
                    config.connectTimeoutMillis());
            socket.setSoTimeout(config.tunnelTimeoutMillis());
            return socket;
        } catch (IOException e) {
            close(socket);
            throw e;
        }
    }

    public void relay(Socket clientSocket, Socket upstreamSocket)
            throws InterruptedException {
        dataRelay.relay(clientSocket, upstreamSocket);
    }

    private boolean isRestricted(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Cleanup failure cannot change the original connection error.
        }
    }
}
