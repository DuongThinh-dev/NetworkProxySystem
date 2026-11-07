package com.networkproxy.handler;

import java.io.IOException;
import java.net.Socket;
import java.util.EnumMap;
import java.util.Map;

//Class này chịu trách nhiệm chuyển Socket đến Handler tương ứng.
public class HandlerDispatcher {

    public enum Protocol {
        HTTP,
        HTTPS,
        SSH
    }

    private final Map<Protocol, ProtocolHandler> handlers =
            new EnumMap<>(Protocol.class);

    public void register(
            Protocol protocol,
            ProtocolHandler handler
    ) {

        handlers.put(protocol, handler);
    }

    public void dispatch(
            Protocol protocol,
            Socket clientSocket
    ) throws IOException {

        ProtocolHandler handler = handlers.get(protocol);

        if (handler == null) {
            throw new IOException(
                    "Chua dang ky Handler cho giao thuc: " + protocol
            );
        }

        handler.handle(clientSocket);
    }
}