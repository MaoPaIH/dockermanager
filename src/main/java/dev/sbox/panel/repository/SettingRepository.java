package dev.sbox.panel.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@DependsOn("databaseInitializer")
public class SettingRepository {

    private final JdbcTemplate jdbc;

    public SettingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> findAll() {
        var rows = jdbc.query("select key, value from settings order by key",
                (rs, rowNum) -> Map.entry(rs.getString("key"), rs.getString("value")));
        var result = new LinkedHashMap<String, String>();
        for (var row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }

    public void put(String key, String value) {
        jdbc.update("""
                insert into settings(key, value, updated_at)
                values(?, ?, ?)
                on conflict(key) do update set value = excluded.value, updated_at = excluded.updated_at
                """, key, value == null ? "" : value, Instant.now().toString());
    }

    public void putAll(Map<String, String> values) {
        values.forEach(this::put);
    }
}
