package dev.sbox.panel.repository;

import dev.sbox.panel.domain.AuditLevel;
import dev.sbox.panel.domain.AuditLog;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
@DependsOn("databaseInitializer")
public class AuditLogRepository {

    private final JdbcTemplate jdbc;

    public AuditLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(AuditLevel level, String actor, String action, String targetType, Long targetId,
                    String targetName, String message, String details) {
        jdbc.update("""
                insert into audit_logs(created_at, level, actor, action, target_type, target_id, target_name, message, details)
                values(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Instant.now().toString(),
                level.name(),
                actor == null || actor.isBlank() ? "system" : actor,
                action,
                targetType,
                targetId,
                targetName,
                message,
                details);
    }

    public List<AuditLog> recent(int limit) {
        return jdbc.query("""
                select * from audit_logs
                order by created_at desc, id desc
                limit ?
                """, this::mapRow, limit);
    }

    public List<AuditLog> forNode(long nodeId, int limit) {
        return jdbc.query("""
                select * from audit_logs
                where target_type = 'node' and target_id = ?
                order by created_at desc, id desc
                limit ?
                """, this::mapRow, nodeId, limit);
    }

    private AuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLog(
                rs.getLong("id"),
                Instant.parse(rs.getString("created_at")),
                AuditLevel.valueOf(rs.getString("level")),
                rs.getString("actor"),
                rs.getString("action"),
                rs.getString("target_type"),
                nullableLong(rs, "target_id"),
                rs.getString("target_name"),
                rs.getString("message"),
                rs.getString("details"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }
}
