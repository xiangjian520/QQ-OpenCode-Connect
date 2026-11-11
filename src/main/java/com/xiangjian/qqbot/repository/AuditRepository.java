package com.xiangjian.qqbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String actorId, String action, String target, String detail) {
        jdbc.update("INSERT INTO audit_log(actor_id, action, target, detail, created_at) VALUES (?, ?, ?, ?, ?)",
                actorId, action, target, detail, System.currentTimeMillis());
    }

    public List<Map<String, Object>> recent(int limit) {
        return jdbc.queryForList("SELECT * FROM audit_log ORDER BY id DESC LIMIT ?", Math.max(1, Math.min(limit, 500)));
    }

    public int deleteOlderThan(long cutoff) {
        return jdbc.update("DELETE FROM audit_log WHERE created_at < ?", cutoff);
    }
}
