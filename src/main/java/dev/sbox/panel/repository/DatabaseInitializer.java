package dev.sbox.panel.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        jdbc.execute("""
                create table if not exists settings (
                    key text primary key,
                    value text not null,
                    updated_at text not null
                )
                """);
        jdbc.execute("""
                create table if not exists nodes (
                    id integer primary key autoincrement,
                    name text not null,
                    container_name text not null unique,
                    container_id text,
                    status text not null,
                    image_name text not null,
                    pull_latest integer not null,
                    start_port integer not null,
                    port_block_size integer not null,
                    uuid text not null,
                    subscription_token text not null unique,
                    subscription_enabled integer not null,
                    protocols text not null,
                    server_ip text not null,
                    cdn text,
                    node_name text not null,
                    argo_domain text,
                    argo_auth text,
                    reality_private text,
                    extra_env text,
                    env_json text,
                    traffic_limit_bytes integer,
                    traffic_used_bytes integer not null default 0,
                    last_rx_bytes integer not null default 0,
                    last_tx_bytes integer not null default 0,
                    expires_at text,
                    created_at text not null,
                    updated_at text not null,
                    last_started_at text,
                    stopped_at text,
                    last_stats_at text,
                    stop_reason text,
                    current_argo_url text
                )
                """);
        jdbc.execute("""
                create table if not exists audit_logs (
                    id integer primary key autoincrement,
                    created_at text not null,
                    level text not null,
                    actor text not null,
                    action text not null,
                    target_type text,
                    target_id integer,
                    target_name text,
                    message text not null,
                    details text
                )
                """);
        jdbc.execute("create index if not exists idx_nodes_status on nodes(status)");
        jdbc.execute("create index if not exists idx_nodes_ports on nodes(start_port, port_block_size)");
        jdbc.execute("create index if not exists idx_audit_logs_created on audit_logs(created_at)");
    }
}
