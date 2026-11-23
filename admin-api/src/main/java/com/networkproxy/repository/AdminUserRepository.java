package com.networkproxy.repository;

import com.networkproxy.entity.AdminUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class AdminUserRepository {

    private static final String SELECT = """
            SELECT UserId, Username, PasswordHash, DisplayName,
                   IsEnabled, CreatedAt, LastLoginAt
            FROM dbo.AdminUsers
            """;

    private static final RowMapper<AdminUser> MAPPER = (rs, rowNum) ->
            new AdminUser(
                    rs.getLong("UserId"),
                    rs.getString("Username"),
                    rs.getString("PasswordHash"),
                    rs.getString("DisplayName"),
                    rs.getBoolean("IsEnabled"),
                    toLocalDateTime(rs.getTimestamp("CreatedAt")),
                    toLocalDateTime(rs.getTimestamp("LastLoginAt")));

    private final NamedParameterJdbcTemplate jdbc;

    public AdminUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AdminUser> findByUsername(String username) {
        return jdbc.query(
                        SELECT + " WHERE Username = :username",
                        new MapSqlParameterSource("username", username),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public Optional<AdminUser> findById(long userId) {
        return jdbc.query(
                        SELECT + " WHERE UserId = :userId",
                        new MapSqlParameterSource("userId", userId),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public long count() {
        Long value = jdbc.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.AdminUsers",
                new MapSqlParameterSource(),
                Long.class);
        return value == null ? 0 : value;
    }

    public void insert(String username, String passwordHash, String displayName) {
        jdbc.update("""
                        INSERT INTO dbo.AdminUsers
                            (Username, PasswordHash, DisplayName)
                        VALUES
                            (:username, :passwordHash, :displayName)
                        """,
                new MapSqlParameterSource()
                        .addValue("username", username)
                        .addValue("passwordHash", passwordHash)
                        .addValue("displayName", displayName, Types.NVARCHAR));
    }

    public void updateLastLogin(long userId) {
        jdbc.update("""
                        UPDATE dbo.AdminUsers
                        SET LastLoginAt = SYSUTCDATETIME()
                        WHERE UserId = :userId
                        """,
                new MapSqlParameterSource("userId", userId));
    }

    public void updatePassword(long userId, String passwordHash) {
        jdbc.update("""
                        UPDATE dbo.AdminUsers
                        SET PasswordHash = :passwordHash
                        WHERE UserId = :userId
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("passwordHash", passwordHash));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
