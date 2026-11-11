package com.xiangjian.qqbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OpenCodeCustomModelRepository {
    private final JdbcTemplate jdbc;

    public OpenCodeCustomModelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CustomModel> list() {
        return jdbc.query("""
                SELECT provider_id, model_id, display_name, variants
                FROM opencode_custom_models ORDER BY updated_at DESC, provider_id ASC, model_id ASC
                """, (rs, rowNum) -> new CustomModel(
                rs.getString("provider_id"),
                rs.getString("model_id"),
                rs.getString("display_name"),
                rs.getString("variants")
        ));
    }

    public Optional<CustomModel> find(String providerId, String modelId) {
        return jdbc.query("""
                SELECT provider_id, model_id, display_name, variants
                FROM opencode_custom_models WHERE provider_id = ? AND model_id = ?
                """, (rs, rowNum) -> new CustomModel(
                rs.getString("provider_id"),
                rs.getString("model_id"),
                rs.getString("display_name"),
                rs.getString("variants")
        ), providerId, modelId).stream().findFirst();
    }

    public void save(String providerId, String modelId, String displayName, String variants) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO opencode_custom_models
                    (provider_id, model_id, display_name, variants, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), variants = VALUES(variants),
                    updated_at = VALUES(updated_at)
                """, providerId, modelId, displayName, variants, now, now);
    }

    public int delete(String providerId, String modelId) {
        return jdbc.update("DELETE FROM opencode_custom_models WHERE provider_id = ? AND model_id = ?",
                providerId, modelId);
    }

    public record CustomModel(String providerId, String modelId, String displayName, String variants) {}
}
