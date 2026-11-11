package com.xiangjian.qqbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class SettingsRepository {
    private final JdbcTemplate jdbc;

    public SettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> all() {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT setting_key, setting_value FROM settings ORDER BY setting_key",
                rs -> {
                    result.put(rs.getString(1), rs.getString(2));
                });
        return result;
    }

    public void put(String key, String value) {
        jdbc.update("""
                INSERT INTO settings(setting_key, setting_value, updated_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)
                """, key, value, System.currentTimeMillis());
    }
}
