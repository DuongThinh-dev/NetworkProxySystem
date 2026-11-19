package com.networkproxy.logging;

import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionLogWriter {
    void insert(ConnectionLog log) throws SQLException;
}
