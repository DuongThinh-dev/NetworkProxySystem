package com.networkproxy.repository;

import com.networkproxy.dto.PageResponse;
import com.networkproxy.dto.TopTarget;
import com.networkproxy.entity.ConnectionLog;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class LogRepository {

    private static final String SELECT = """
            SELECT LogId, OccurredAt, ClientIp, Target, TargetPort, Protocol,
                   Decision, MatchedRuleId, ReasonCode, ExecutionStatus, ErrorMessage
            FROM ConnectionLogs
            """;

    private static final RowMapper<ConnectionLog> MAPPER = (rs, rowNum) ->
            new ConnectionLog(
                    rs.getLong("LogId"),
                    toLocalDateTime(rs.getTimestamp("OccurredAt")),
                    rs.getString("ClientIp"),
                    rs.getString("Target"),
                    rs.getInt("TargetPort"),
                    rs.getString("Protocol"),
                    rs.getString("Decision"),
                    nullableLong(rs, "MatchedRuleId"),
                    rs.getString("ReasonCode"),
                    rs.getString("ExecutionStatus"),
                    rs.getString("ErrorMessage"));

    private final NamedParameterJdbcTemplate jdbc;

    public LogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ConnectionLog> findPage(
            String clientIp,
            String target,
            String protocol,
            String decision,
            String executionStatus,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        QueryFilter filter = buildFilter(clientIp, target, protocol, decision,
                executionStatus, from, to);
        long total = count("SELECT COUNT(*) FROM ConnectionLogs"
                + filter.where(), filter.params());

        MapSqlParameterSource params = filter.params()
                .addValue("offset", page * size)
                .addValue("size", size);
        List<ConnectionLog> content = jdbc.query(
                SELECT + filter.where()
                        + " ORDER BY OccurredAt DESC, LogId DESC"
                        + " LIMIT :size OFFSET :offset",
                params,
                MAPPER);
        return PageResponse.of(content, page, size, total);
    }

    public Optional<ConnectionLog> findById(long id) {
        return jdbc.query(
                        SELECT + " WHERE LogId = :id",
                        new MapSqlParameterSource("id", id),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public long countAll() {
        return count("SELECT COUNT(*) FROM ConnectionLogs",
                new MapSqlParameterSource());
    }

    public long countByDecision(String decision) {
        return count("SELECT COUNT(*) FROM ConnectionLogs WHERE Decision = :decision",
                new MapSqlParameterSource("decision", decision));
    }

    public long countByStatus(String executionStatus) {
        return count("SELECT COUNT(*) FROM ConnectionLogs WHERE ExecutionStatus = :status",
                new MapSqlParameterSource("status", executionStatus));
    }

    public long countSince(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM ConnectionLogs WHERE OccurredAt >= :since",
                new MapSqlParameterSource("since", Timestamp.valueOf(since)));
    }

    public long countDistinctClients() {
        return count("""
                        SELECT COUNT(*)
                        FROM (SELECT DISTINCT ClientIp FROM ConnectionLogs) clients
                        """,
                new MapSqlParameterSource());
    }

    public List<TopTarget> topTargets(int limit) {
        return jdbc.query("""
                        SELECT Target, COUNT(*) AS Total
                        FROM ConnectionLogs
                        GROUP BY Target
                        ORDER BY Total DESC, Target ASC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> new TopTarget(
                        rs.getString("Target"),
                        rs.getLong("Total")));
    }

    public List<ConnectionLog> recent(int limit) {
        return jdbc.query(
                SELECT + " ORDER BY OccurredAt DESC, LogId DESC"
                        + " LIMIT :limit",
                new MapSqlParameterSource("limit", limit),
                MAPPER);
    }

    private QueryFilter buildFilter(
            String clientIp,
            String target,
            String protocol,
            String decision,
            String executionStatus,
            LocalDateTime from,
            LocalDateTime to
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (clientIp != null && !clientIp.isBlank()) {
            where.append(" AND ClientIp LIKE :clientIp");
            params.addValue("clientIp", "%" + clientIp.trim() + "%");
        }
        if (target != null && !target.isBlank()) {
            where.append(" AND Target LIKE :target");
            params.addValue("target", "%" + target.trim() + "%");
        }
        if (protocol != null) {
            where.append(" AND Protocol = :protocol");
            params.addValue("protocol", protocol);
        }
        if (decision != null) {
            where.append(" AND Decision = :decision");
            params.addValue("decision", decision);
        }
        if (executionStatus != null) {
            where.append(" AND ExecutionStatus = :executionStatus");
            params.addValue("executionStatus", executionStatus);
        }
        if (from != null) {
            where.append(" AND OccurredAt >= :from");
            params.addValue("from", Timestamp.valueOf(from));
        }
        if (to != null) {
            where.append(" AND OccurredAt < :to");
            params.addValue("to", Timestamp.valueOf(to));
        }
        return new QueryFilter(where.toString(), params);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record QueryFilter(String where, MapSqlParameterSource params) {
    }
}
