package com.networkproxy.https;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectRequestParserTest {

    private final ConnectRequestParser parser = new ConnectRequestParser();

    @Test
    void parsesValidConnectRequest() {
        ConnectRequest request =
                parser.parse("CONNECT example.com:443 HTTP/1.1");

        assertEquals("example.com", request.host());
        assertEquals(443, request.port());
    }

    @Test
    void rejectsNonConnectRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("GET http://example.com/ HTTP/1.1"));
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("CONNECT example.com:0 HTTP/1.1"));
    }

    @Test
    void rejectsMissingPort() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("CONNECT example.com HTTP/1.1"));
    }
}
