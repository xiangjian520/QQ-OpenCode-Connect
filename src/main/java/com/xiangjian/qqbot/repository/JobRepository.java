package com.xiangjian.qqbot.repository;

import com.xiangjian.qqbot.domain.ChatJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class JobRepository {
    private static final RowMapper<ChatJob> MAPPER = (rs, rowNum) -> new ChatJob(
            rs.getLong("id"),
            rs.getString("conversation_key"),
            rs.getString("inbound_message_id"),
            rs.getString("text"),
            rs.getString("status"),
            rs.getInt("attempts"),
            rs.getString("error"),
            rs.getLong("created_at"),
            rs.getObject("started_at", Long.class),
            rs.getObject("completed_at", Long.class)
    );

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean enqueue(String conversationKey, String inboundMessageId, String text) {
        int changed = jdbc.update("""
                INSERT IGNORE INTO jobs
                    (conversation_key, inbound_message_id, text, status, attempts, created_at)
                VALUES (?, ?, ?, 'PENDING', 0, ?)
                """, conversationKey, inboundMessageId, text, System.currentTimeMillis());
        return changed > 0;
    }

    public int activeCount(String conversationKey) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM jobs WHERE conversation_key = ? AND status IN ('PENDING', 'RUNNING')
                """, Integer.class, conversationKey);
        return value == null ? 0 : value;
    }

    public ChatJob claimNext(Set<String> busyConversations) {
        List<ChatJob> candidates = jdbc.query(
                "SELECT * FROM jobs WHERE status = 'PENDING' ORDER BY id ASC LIMIT 100", MAPPER);
        for (ChatJob candidate : candidates) {
            if (busyConversations.contains(candidate.conversationKey())) {
                continue;
            }
            int changed = jdbc.update("""
                    UPDATE jobs SET status = 'RUNNING', started_at = ?, attempts = attempts + 1
                    WHERE id = ? AND status = 'PENDING'
                    """, System.currentTimeMillis(), candidate.id());
            if (changed == 1) {
                return find(candidate.id()).orElse(null);
            }
        }
        return null;
    }

    public java.util.Optional<ChatJob> find(long id) {
        return jdbc.query("SELECT * FROM jobs WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public String status(long id) {
        List<String> rows = jdbc.query("SELECT status FROM jobs WHERE id = ?", (rs, i) -> rs.getString(1), id);
        return rows.isEmpty() ? "MISSING" : rows.getFirst();
    }

    public void complete(long id) {
        jdbc.update("UPDATE jobs SET status = 'DONE', completed_at = ?, error = NULL WHERE id = ? AND status = 'RUNNING'",
                System.currentTimeMillis(), id);
    }

    public void fail(long id, String error) {
        jdbc.update("UPDATE jobs SET status = 'FAILED', completed_at = ?, error = ? WHERE id = ? AND status = 'RUNNING'",
                System.currentTimeMillis(), truncate(error), id);
    }

    public int cancelConversation(String conversationKey) {
        long now = System.currentTimeMillis();
        return jdbc.update("""
                UPDATE jobs SET status = 'CANCELLED', completed_at = ?, error = 'cancelled by user'
                WHERE conversation_key = ? AND status IN ('PENDING', 'RUNNING')
                """, now, conversationKey);
    }

    public void recoverRunning() {
        jdbc.update("UPDATE jobs SET status = 'PENDING', started_at = NULL WHERE status = 'RUNNING'");
    }

    public int countByStatus(String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM jobs WHERE status = ?", Integer.class, status);
        return count == null ? 0 : count;
    }

    public int deleteCompletedOlderThan(long cutoff) {
        return jdbc.update("DELETE FROM jobs WHERE status IN ('DONE', 'FAILED', 'CANCELLED') AND completed_at < ?", cutoff);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
