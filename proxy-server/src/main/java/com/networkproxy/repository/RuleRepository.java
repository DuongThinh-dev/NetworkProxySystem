package com.networkproxy.repository;

import com.networkproxy.config.DatabaseConfig;
import com.networkproxy.security.AccessRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class RuleRepository {

    private static final String FIND_ENABLED_SQL = """
            SELECT RuleId, ClientIp, Target, TargetPort, Protocol,
                   [Action], Priority, IsEnabled
            FROM dbo.AccessRules
            WHERE IsEnabled = 1
            ORDER BY Priority DESC, RuleId ASC
            """;

    private final SqlServerConnectionFactory connectionFactory;

    public RuleRepository(DatabaseConfig config) {
        this(new SqlServerConnectionFactory(config));
    }

    public RuleRepository(SqlServerConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public List<AccessRule> findEnabled() throws SQLException {
        List<AccessRule> rules = new ArrayList<>();
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ENABLED_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                int port = resultSet.getInt("TargetPort");
                Integer targetPort = resultSet.wasNull() ? null : port;
                rules.add(new AccessRule(
                        resultSet.getLong("RuleId"),
                        resultSet.getString("ClientIp"),
                        resultSet.getString("Target"),
                        targetPort,
                        resultSet.getString("Protocol"),
                        AccessRule.Action.valueOf(resultSet.getString("Action")),
                        resultSet.getInt("Priority"),
                        resultSet.getBoolean("IsEnabled")
                ));
            }
        }
        return List.copyOf(rules);
    }
}
