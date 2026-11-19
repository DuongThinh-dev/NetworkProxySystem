package com.networkproxy.repository;

import com.networkproxy.config.DatabaseConfig;
import com.networkproxy.logging.ConnectionLog;
import com.networkproxy.logging.ConnectionLogWriter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class LogRepository implements ConnectionLogWriter {

    private static final String INSERT_SQL = """
            INSERT INTO dbo.ConnectionLogs
                (OccurredAt, ClientIp, Target, TargetPort, Protocol, Decision,
                 MatchedRuleId, ReasonCode, ExecutionStatus, ErrorMessage)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final SqlServerConnectionFactory connectionFactory;

    public LogRepository(DatabaseConfig config) {
        this(new SqlServerConnectionFactory(config));
    }

    public LogRepository(SqlServerConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void insert(ConnectionLog log) throws SQLException {
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setObject(1, log.occurredAtUtc());
            statement.setString(2, log.clientIp());
            statement.setString(3, log.target());
            statement.setInt(4, log.targetPort());
            statement.setString(5, log.protocol());
            statement.setString(6, log.decision().name());
            if (log.matchedRuleId() == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7, log.matchedRuleId());
            }
            statement.setString(8, log.reasonCode());
            statement.setString(9, log.executionStatus().name());
            statement.setString(10, log.errorMessage());
            statement.executeUpdate();
        }
    }
}
