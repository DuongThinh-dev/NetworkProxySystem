package com.networkproxy.https;

import java.io.IOException;

public class HttpsException extends IOException {

    private final int statusCode;

    public HttpsException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
