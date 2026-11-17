package com.networkproxy.https;

import java.net.IDN;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectRequestParser {

    private static final Pattern REQUEST_LINE =
            Pattern.compile("^CONNECT\\s+([^\\s]+)\\s+HTTP/1\\.[01]$", Pattern.CASE_INSENSITIVE);

    public ConnectRequest parse(String requestLine) {
        if (requestLine == null) {
            throw new IllegalArgumentException("Missing CONNECT request");
        }
        Matcher matcher = REQUEST_LINE.matcher(requestLine.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid CONNECT request");
        }

        String authority = matcher.group(1);
        int separator = authority.lastIndexOf(':');
        if (separator <= 0 || separator == authority.length() - 1
                || authority.indexOf(':') != separator) {
            throw new IllegalArgumentException("CONNECT target must be host:port");
        }

        String host = authority.substring(0, separator).trim();
        int port;
        try {
            port = Integer.parseInt(authority.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CONNECT port", e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid CONNECT port");
        }
        try {
            host = IDN.toASCII(host);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CONNECT hostname", e);
        }
        if (host.isBlank() || host.length() > 253
                || host.startsWith(".") || host.endsWith(".")) {
            throw new IllegalArgumentException("Invalid CONNECT hostname");
        }
        return new ConnectRequest(host, port);
    }
}
