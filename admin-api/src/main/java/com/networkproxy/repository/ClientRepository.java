package com.networkproxy.repository;

import com.networkproxy.dto.ClientSummary;
import com.networkproxy.dto.PageResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class ClientRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ClientRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ClientSummary> findPage(String q, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (q != null && !q.isBlank()) {
            where.append(" AND ClientIp LIKE :q");
            params.addValue("q", "%" + q.trim() + "%");
        }

        Long totalValue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT DISTINCT ClientIp"
                        + " FROM ConnectionLogs" + where + ") clients",
                params,
                Long.class);
        long total = totalValue == null ? 0 : totalValue;

        params.addValue("offset", page * size);
        params.addValue("size", size);

        String sql = """
                SELECT ClientIp,
                       COUNT(*) AS TotalRequests,
                       SUM(CASE WHEN Decision = 'ALLOW' THEN 1 ELSE 0 END) AS Allowed,
                       SUM(CASE WHEN Decision = 'BLOCK' THEN 1 ELSE 0 END) AS Blocked,
                       SUM(CASE WHEN ExecutionStatus = 'ERROR' THEN 1 ELSE 0 END) AS Errors,
                       MAX(OccurredAt) AS LastSeen
                FROM ConnectionLogs
                """ + where + """
                 GROUP BY ClientIp
                 ORDER BY LastSeen DESC, ClientIp ASC
                 LIMIT :size OFFSET :offset
                """;

        List<ClientSummary> content = jdbc.query(sql, params, (rs, rowNum) ->
                new ClientSummary(
                        rs.getString("ClientIp"),
                        rs.getLong("TotalRequests"),
                        rs.getLong("Allowed"),
                        rs.getLong("Blocked"),
                        rs.getLong("Errors"),
                        toInstant(rs.getTimestamp("LastSeen"))));

        return PageResponse.of(content, page, size, total);
    }

    public long countDistinct() {
        Long value = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM (SELECT DISTINCT ClientIp FROM ConnectionLogs) clients
                        """,
                new MapSqlParameterSource(),
                Long.class);
        return value == null ? 0 : value;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
