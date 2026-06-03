package dev.sbox.panel.repository;

import dev.sbox.panel.domain.NodeInstance;
import dev.sbox.panel.domain.NodeStatus;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@DependsOn("databaseInitializer")
public class NodeRepository {

    private final JdbcTemplate jdbc;

    public NodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(NodeInstance node) {
        var now = Instant.now();
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into nodes(name, container_name, container_id, status, image_name, pull_latest,
                      start_port, port_block_size, uuid, subscription_token, subscription_enabled, protocols,
                      server_ip, cdn, node_name, argo_domain, argo_auth, reality_private, extra_env, env_json,
                      traffic_limit_bytes, traffic_used_bytes, last_rx_bytes, last_tx_bytes, expires_at,
                      created_at, updated_at, last_started_at, stopped_at, last_stats_at, stop_reason, current_argo_url)
                    values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindInsert(ps, node);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("SQLite did not return a generated node id");
        }
        node.setId(key.longValue());
        return key.longValue();
    }

    public List<NodeInstance> findAllActive() {
        return jdbc.query("""
                select * from nodes
                where status <> 'DELETED'
                order by created_at desc
                """, this::mapRow);
    }

    public List<NodeInstance> findAllForMonitor() {
        return jdbc.query("""
                select * from nodes
                where status in ('CREATING', 'RUNNING', 'STOPPED', 'ERROR')
                order by id
                """, this::mapRow);
    }

    public Optional<NodeInstance> findById(long id) {
        var rows = jdbc.query("select * from nodes where id = ?", this::mapRow, id);
        return rows.stream().findFirst();
    }

    public Optional<NodeInstance> findBySubscriptionToken(String token) {
        var rows = jdbc.query("""
                select * from nodes
                where subscription_token = ? and status <> 'DELETED'
                """, this::mapRow, token);
        return rows.stream().findFirst();
    }

    public List<NodeInstance> findPortOwners() {
        return jdbc.query("""
                select * from nodes
                where status <> 'DELETED'
                order by start_port
                """, this::mapRow);
    }

    public void updateContainerCreated(long id, String containerId, NodeStatus status) {
        jdbc.update("""
                update nodes
                set container_id = ?, status = ?, updated_at = ?, last_started_at = ?, stop_reason = null
                where id = ?
                """, containerId, status.name(), Instant.now().toString(), Instant.now().toString(), id);
    }

    public void updateStatus(long id, NodeStatus status, String reason) {
        jdbc.update("""
                update nodes
                set status = ?, stop_reason = ?, stopped_at = case when ? in ('STOPPED','EXPIRED','TRAFFIC_LIMITED','ERROR') then ? else stopped_at end,
                    updated_at = ?
                where id = ?
                """, status.name(), reason, status.name(), Instant.now().toString(), Instant.now().toString(), id);
    }

    public void markStarted(long id, NodeStatus status) {
        jdbc.update("""
                update nodes
                set status = ?, last_started_at = ?, stopped_at = null, stop_reason = null, updated_at = ?
                where id = ?
                """, status.name(), Instant.now().toString(), Instant.now().toString(), id);
    }

    public void updateEditable(long id, String name, Long trafficLimitBytes, Instant expiresAt, boolean subscriptionEnabled) {
        jdbc.update("""
                update nodes
                set name = ?, traffic_limit_bytes = ?, expires_at = ?, subscription_enabled = ?, updated_at = ?
                where id = ?
                """, name, trafficLimitBytes, format(expiresAt), subscriptionEnabled ? 1 : 0, Instant.now().toString(), id);
    }

    public void updateTraffic(long id, long usedBytes, long lastRxBytes, long lastTxBytes, Instant sampledAt) {
        jdbc.update("""
                update nodes
                set traffic_used_bytes = ?, last_rx_bytes = ?, last_tx_bytes = ?, last_stats_at = ?, updated_at = ?
                where id = ?
                """, usedBytes, lastRxBytes, lastTxBytes, sampledAt.toString(), Instant.now().toString(), id);
    }

    public void resetTraffic(long id) {
        jdbc.update("""
                update nodes
                set traffic_used_bytes = 0, last_rx_bytes = 0, last_tx_bytes = 0, last_stats_at = null, updated_at = ?
                where id = ?
                """, Instant.now().toString(), id);
    }

    public void updateCurrentArgoUrl(long id, String currentArgoUrl) {
        jdbc.update("""
                update nodes
                set current_argo_url = ?, updated_at = ?
                where id = ?
                """, currentArgoUrl, Instant.now().toString(), id);
    }

    private void bindInsert(PreparedStatement ps, NodeInstance node) throws SQLException {
        int i = 1;
        ps.setString(i++, node.getName());
        ps.setString(i++, node.getContainerName());
        ps.setString(i++, node.getContainerId());
        ps.setString(i++, node.getStatus().name());
        ps.setString(i++, node.getImageName());
        ps.setInt(i++, node.isPullLatest() ? 1 : 0);
        ps.setInt(i++, node.getStartPort());
        ps.setInt(i++, node.getPortBlockSize());
        ps.setString(i++, node.getUuid());
        ps.setString(i++, node.getSubscriptionToken());
        ps.setInt(i++, node.isSubscriptionEnabled() ? 1 : 0);
        ps.setString(i++, node.getProtocols());
        ps.setString(i++, node.getServerIp());
        ps.setString(i++, node.getCdn());
        ps.setString(i++, node.getNodeName());
        ps.setString(i++, node.getArgoDomain());
        ps.setString(i++, node.getArgoAuth());
        ps.setString(i++, node.getRealityPrivate());
        ps.setString(i++, node.getExtraEnv());
        ps.setString(i++, node.getEnvJson());
        if (node.getTrafficLimitBytes() == null) {
            ps.setObject(i++, null);
        } else {
            ps.setLong(i++, node.getTrafficLimitBytes());
        }
        ps.setLong(i++, node.getTrafficUsedBytes());
        ps.setLong(i++, node.getLastRxBytes());
        ps.setLong(i++, node.getLastTxBytes());
        ps.setString(i++, format(node.getExpiresAt()));
        ps.setString(i++, node.getCreatedAt().toString());
        ps.setString(i++, node.getUpdatedAt().toString());
        ps.setString(i++, format(node.getLastStartedAt()));
        ps.setString(i++, format(node.getStoppedAt()));
        ps.setString(i++, format(node.getLastStatsAt()));
        ps.setString(i++, node.getStopReason());
        ps.setString(i, node.getCurrentArgoUrl());
    }

    private NodeInstance mapRow(ResultSet rs, int rowNum) throws SQLException {
        var node = new NodeInstance();
        node.setId(rs.getLong("id"));
        node.setName(rs.getString("name"));
        node.setContainerName(rs.getString("container_name"));
        node.setContainerId(rs.getString("container_id"));
        node.setStatus(NodeStatus.valueOf(rs.getString("status")));
        node.setImageName(rs.getString("image_name"));
        node.setPullLatest(rs.getInt("pull_latest") == 1);
        node.setStartPort(rs.getInt("start_port"));
        node.setPortBlockSize(rs.getInt("port_block_size"));
        node.setUuid(rs.getString("uuid"));
        node.setSubscriptionToken(rs.getString("subscription_token"));
        node.setSubscriptionEnabled(rs.getInt("subscription_enabled") == 1);
        node.setProtocols(rs.getString("protocols"));
        node.setServerIp(rs.getString("server_ip"));
        node.setCdn(rs.getString("cdn"));
        node.setNodeName(rs.getString("node_name"));
        node.setArgoDomain(rs.getString("argo_domain"));
        node.setArgoAuth(rs.getString("argo_auth"));
        node.setRealityPrivate(rs.getString("reality_private"));
        node.setExtraEnv(rs.getString("extra_env"));
        node.setEnvJson(rs.getString("env_json"));
        node.setTrafficLimitBytes(nullableLong(rs, "traffic_limit_bytes"));
        node.setTrafficUsedBytes(rs.getLong("traffic_used_bytes"));
        node.setLastRxBytes(rs.getLong("last_rx_bytes"));
        node.setLastTxBytes(rs.getLong("last_tx_bytes"));
        node.setExpiresAt(parse(rs.getString("expires_at")));
        node.setCreatedAt(parseRequired(rs.getString("created_at")));
        node.setUpdatedAt(parseRequired(rs.getString("updated_at")));
        node.setLastStartedAt(parse(rs.getString("last_started_at")));
        node.setStoppedAt(parse(rs.getString("stopped_at")));
        node.setLastStatsAt(parse(rs.getString("last_stats_at")));
        node.setStopReason(rs.getString("stop_reason"));
        node.setCurrentArgoUrl(rs.getString("current_argo_url"));
        return node;
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant parse(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static Instant parseRequired(String value) {
        return Instant.parse(value);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }
}
