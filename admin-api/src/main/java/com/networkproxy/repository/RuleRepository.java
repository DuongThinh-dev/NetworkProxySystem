package com.networkproxy.repository;

import com.networkproxy.dto.PageResponse;
import com.networkproxy.entity.AccessRule;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RuleRepository {

    private static final String SELECT = """
            SELECT RuleId, ClientIp, Target, TargetPort, Protocol,
                   Action, Priority, IsEnabled, CreatedAt, UpdatedAt
            FROM AccessRules
            """;

    private static final RowMapper<AccessRule> MAPPER = (rs, rowNum) ->
            new AccessRule(
                    rs.getLong("RuleId"),
                    rs.getString("ClientIp"),
                    rs.getString("Target"),
                    nullableInt(rs, "TargetPort"),
                    rs.getString("Protocol"),
                    AccessRule.Action.valueOf(rs.getString("Action")),
                    rs.getInt("Priority"),
                    rs.getBoolean("IsEnabled"),
                    toLocalDateTime(rs.getTimestamp("CreatedAt")),
                    toLocalDateTime(rs.getTimestamp("UpdatedAt")));

    private final NamedParameterJdbcTemplate jdbc;

    public RuleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<AccessRule> findPage(
            String q,
            Boolean enabled,
            int page,
            int size
    ) {
        QueryFilter filter = buildFilter(q, enabled);
        long total = count("SELECT COUNT(*) FROM AccessRules"
                + filter.where(), filter.params());

        MapSqlParameterSource params = filter.params()
                .addValue("offset", page * size)
                .addValue("size", size);
        List<AccessRule> content = jdbc.query(
                SELECT + filter.where()
                        + " ORDER BY Priority DESC, RuleId ASC"
                        + " LIMIT :size OFFSET :offset",
                params,
                MAPPER);
        return PageResponse.of(content, page, size, total);
    }

    public Optional<AccessRule> findById(long id) {
        return jdbc.query(
                        SELECT + " WHERE RuleId = :id",
                        new MapSqlParameterSource("id", id),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public long insert(AccessRule rule) {
        MapSqlParameterSource params = ruleParams(rule);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                        INSERT INTO AccessRules
                            (ClientIp, Target, TargetPort, Protocol,
                             Action, Priority, IsEnabled)
                        VALUES
                            (:clientIp, :target, :targetPort, :protocol,
                             :action, :priority, :enabled)
                        """,
                params,
                keyHolder,
                new String[]{"RuleId"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Không lấy được RuleId sau khi insert");
        }
        return key.longValue();
    }

    public void update(long id, AccessRule rule) {
        MapSqlParameterSource params = ruleParams(rule)
                .addValue("id", id);
        jdbc.update("""
                        UPDATE AccessRules
                        SET ClientIp = :clientIp,
                            Target = :target,
                            TargetPort = :targetPort,
                            Protocol = :protocol,
                            Action = :action,
                            Priority = :priority,
                            IsEnabled = :enabled,
                            UpdatedAt = UTC_TIMESTAMP(3)
                        WHERE RuleId = :id
                        """,
                params);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM AccessRules WHERE RuleId = :id",
                new MapSqlParameterSource("id", id));
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("""
                        UPDATE AccessRules
                        SET IsEnabled = :enabled,
                            UpdatedAt = UTC_TIMESTAMP(3)
                        WHERE RuleId = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("enabled", enabled, Types.BIT));
    }

    public long count() {
        return count("SELECT COUNT(*) FROM AccessRules",
                new MapSqlParameterSource());
    }

    public long countEnabled() {
        return count("SELECT COUNT(*) FROM AccessRules WHERE IsEnabled = 1",
                new MapSqlParameterSource());
    }

    private QueryFilter buildFilter(String q, Boolean enabled) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (q != null && !q.isBlank()) {
            where.append("""
                     AND (ClientIp LIKE :q
                          OR Target LIKE :q
                          OR CAST(RuleId AS NVARCHAR(20)) LIKE :q)
                    """);
            params.addValue("q", "%" + q.trim() + "%");
        }
        if (enabled != null) {
            where.append(" AND IsEnabled = :enabled");
            params.addValue("enabled", enabled, Types.BIT);
        }
        return new QueryFilter(where.toString(), params);
    }

    private MapSqlParameterSource ruleParams(AccessRule rule) {
        return new MapSqlParameterSource()
                .addValue("clientIp", rule.clientIp(), Types.NVARCHAR)
                .addValue("target", rule.target(), Types.NVARCHAR)
                .addValue("targetPort", rule.targetPort(), Types.INTEGER)
                .addValue("protocol", rule.protocol(), Types.VARCHAR)
                .addValue("action", rule.action().name(), Types.VARCHAR)
                .addValue("priority", rule.priority(), Types.INTEGER)
                .addValue("enabled", rule.enabled(), Types.BIT);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record QueryFilter(String where, MapSqlParameterSource params) {
    }
}
