package com.networkproxy.https;

import com.networkproxy.network.DataRelay;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpsTunnelManagerTest {

    @Test
    void rejectsDisallowedPortBeforeConnecting() {
        HttpsTunnelManager manager = new HttpsTunnelManager(
                new HttpsConnectionConfig(1000, 1000, Set.of(443), false),
                new DataRelay());

        HttpsException exception = assertThrows(HttpsException.class,
                () -> manager.connect(new ConnectRequest("example.com", 22)));

        org.junit.jupiter.api.Assertions.assertEquals(403,
                exception.getStatusCode());
    }
}
