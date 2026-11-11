package com.xiangjian.qqbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class EntertainmentGameRepository {
    private final JdbcTemplate jdbc;

    public EntertainmentGameRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<GameState> find(String conversationKey) {
        List<GameState> rows = jdbc.query(
                "SELECT game_type, state_json, updated_at FROM entertainment_games WHERE conversation_key = ?",
                (rs, rowNum) -> new GameState(
                        rs.getString("game_type"),
                        rs.getString("state_json"),
                        rs.getLong("updated_at")
                ),
                conversationKey
        );
        return rows.stream().findFirst();
    }

    public void save(String conversationKey, String gameType, String stateJson) {
        jdbc.update("""
                INSERT INTO entertainment_games(conversation_key, game_type, state_json, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE game_type = VALUES(game_type), state_json = VALUES(state_json), updated_at = VALUES(updated_at)
                """, conversationKey, gameType, stateJson, System.currentTimeMillis());
    }

    public void delete(String conversationKey) {
        jdbc.update("DELETE FROM entertainment_games WHERE conversation_key = ?", conversationKey);
    }

    public int countActive() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM entertainment_games", Integer.class);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> recent(int limit) {
        return jdbc.queryForList("""
                SELECT conversation_key, game_type, updated_at
                FROM entertainment_games ORDER BY updated_at DESC LIMIT ?
                """, Math.max(1, Math.min(limit, 100)));
    }

    public record GameState(String gameType, String stateJson, long updatedAt) {}
}
