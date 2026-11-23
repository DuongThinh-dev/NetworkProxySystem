package com.networkproxy.repository;

import com.networkproxy.dto.PageResponse;
import com.networkproxy.entity.AuditLog;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AuditLogRepository {

    private static final String SELECT = """
            SELECT AuditId, UserId, Username, Action, EntityType,
                   EntityId, Details, CreatedAt
            FROM dbo.AdminAuditLogs
            """;

    private static final RowMapper<AuditLog> MAPPER = (rs, rowNum) ->
            new AuditLog(
                    rs.getLong("AuditId"),
                    toLocalDateTime(rs.getTimestamp("CreatedAt")),
                    nullableLong(rs, "UserId"),
                    rs.getString("Username"),
                    rs.getString("Action"),
                    rs.getString("EntityType"),
                    nullableLong(rs, "EntityId"),
                    rs.getString("Details"));

    private final NamedParameterJdbcTemplate jdbc;

    public AuditLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            Long userId,
            String username,
            String action,
            String entityType,
            Long entityId,
            String details
    ) {
        jdbc.update("""
                        INSERT INTO dbo.AdminAuditLogs
                            (UserId, Username, Action, EntityType, EntityId, Details)
                        VALUES
                            (:userId, :username, :action, :entityType, :entityId, :details)
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId, Types.BIGINT)
                        .addValue("username", username, Types.NVARCHAR)
                        .addValue("action", action, Types.NVARCHAR)
                        .addValue("entityType", entityType, Types.NVARCHAR)
                        .addValue("entityId", entityId, Types.BIGINT)
                        .addValue("details", truncate(details, 1000), Types.NVARCHAR));
    }

    public PageResponse<AuditLog> findPage(int page, int size) {
        Long totalValue = jdbc.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.AdminAuditLogs",
                new MapSqlParameterSource(),
                Long.class);
        long total = totalValue == null ? 0 : totalValue;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", page * size)
                .addValue("size", size);
        List<AuditLog> content = jdbc.query(
                SELECT + " ORDER BY CreatedAt DESC, AuditId DESC"
                        + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                params,
                MAPPER);
        return PageResponse.of(content, page, size, total);
    }

    public List<AuditLog> recent(int limit) {
        return jdbc.query(
                SELECT + " ORDER BY CreatedAt DESC, AuditId DESC"
                        + " OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY",
                new MapSqlParameterSource("limit", limit),
                MAPPER);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
