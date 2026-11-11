package com.xiangjian.qqbot.repository;

import com.xiangjian.qqbot.domain.MessageRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepository {
    private static final RowMapper<MessageRow> MAPPER = (rs, rowNum) -> new MessageRow(
            rs.getLong("id"),
            rs.getString("conversation_key"),
            rs.getString("direction"),
            rs.getString("platform_message_id"),
            rs.getString("opencode_message_id"),
            rs.getString("sender_id"),
            rs.getString("content"),
            rs.getString("status"),
            rs.getLong("created_at")
    );

    private final JdbcTemplate jdbc;

    public MessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertInbound(String conversationKey, String platformMessageId, String senderId, String content) {
        int changed = jdbc.update("""
                INSERT IGNORE INTO messages
                    (conversation_key, direction, platform_message_id, sender_id, content, status, created_at)
                VALUES (?, 'IN', ?, ?, ?, 'accepted', ?)
                """, conversationKey, platformMessageId, senderId, content, System.currentTimeMillis());
        return changed > 0;
    }

    public void insertOutbound(String conversationKey, String opencodeMessageId, String senderId,
                               String content, String status) {
        jdbc.update("""
                INSERT INTO messages
                    (conversation_key, direction, opencode_message_id, sender_id, content, status, created_at)
                VALUES (?, 'OUT', ?, ?, ?, ?, ?)
                """, conversationKey, opencodeMessageId, senderId, content, status, System.currentTimeMillis());
    }

    public void markInboundStatus(String platformMessageId, String status) {
        jdbc.update("UPDATE messages SET status = ? WHERE platform_message_id = ?", status, platformMessageId);
    }

    public List<MessageRow> list(String conversationKey, int limit) {
        return jdbc.query("""
                SELECT m.*
                FROM messages m
                JOIN (
                    SELECT id FROM messages
                    WHERE conversation_key = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                ) recent ON recent.id = m.id
                ORDER BY m.created_at ASC, m.id ASC
                """, MAPPER, conversationKey, Math.max(1, Math.min(limit, 500)));
    }

    public int deleteOlderThan(long cutoff) {
        return jdbc.update("DELETE FROM messages WHERE created_at < ?", cutoff);
    }
}
