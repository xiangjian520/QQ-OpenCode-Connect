package com.xiangjian.qqbot.repository;

import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.domain.InboundMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {
    private static final RowMapper<Conversation> MAPPER = ConversationRepository::map;

    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createIfMissing(InboundMessage message) {
        long now = System.currentTimeMillis();
        String displayName = message.isGroup() ? "QQ群 " + message.chatId() : message.displayName();
        int changed = jdbc.update("""
                INSERT IGNORE INTO conversations
                    (conversation_key, platform, chat_type, chat_id, display_name, agent_profile, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'qq-chat', ?, ?)
                """, message.conversationKey(), message.platform(), message.chatType().name(),
                message.chatId(), displayName, now, now);
        if (changed == 0) {
            jdbc.update("UPDATE conversations SET display_name = COALESCE(?, display_name), updated_at = ? WHERE conversation_key = ?",
                    message.isGroup() ? null : message.displayName(), now, message.conversationKey());
        }
    }

    public Conversation createWebConversation(String adminName) {
        String key = "web:WEB:admin";
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT IGNORE INTO conversations
                    (conversation_key, platform, chat_type, chat_id, display_name, agent_profile, created_at, updated_at)
                VALUES (?, 'web', 'WEB', 'admin', ?, 'qq-chat', ?, ?)
                """, key, adminName, now, now);
        return find(key).orElseThrow();
    }

    public Optional<Conversation> find(String key) {
        List<Conversation> rows = jdbc.query("SELECT * FROM conversations WHERE conversation_key = ?", MAPPER, key);
        return rows.stream().findFirst();
    }

    public List<Conversation> list(int limit) {
        return jdbc.query("SELECT * FROM conversations ORDER BY updated_at DESC LIMIT ?", MAPPER, Math.max(1, Math.min(limit, 200)));
    }

    public void updateSession(String key, String sessionId) {
        jdbc.update("UPDATE conversations SET opencode_session_id = ?, updated_at = ? WHERE conversation_key = ?",
                sessionId, System.currentTimeMillis(), key);
    }

    public void clearSession(String key) {
        jdbc.update("""
                UPDATE conversations
                SET opencode_session_id = NULL, code_mode_until = NULL, agent_profile = 'qq-chat', updated_at = ?
                WHERE conversation_key = ?
                """, System.currentTimeMillis(), key);
    }

    public void updateModel(String key, String provider, String modelId, String variant) {
        jdbc.update("""
                UPDATE conversations SET model_provider = ?, model_id = ?, model_variant = ?, updated_at = ?
                WHERE conversation_key = ?
                """, provider, modelId, variant, System.currentTimeMillis(), key);
    }

    public void updateAgent(String key, String agent, Long codeModeUntil) {
        jdbc.update("UPDATE conversations SET agent_profile = ?, code_mode_until = ?, updated_at = ? WHERE conversation_key = ?",
                agent, codeModeUntil, System.currentTimeMillis(), key);
    }

    public List<Conversation> findExpiredCodeModes(long now, int limit) {
        return jdbc.query("""
                SELECT * FROM conversations
                WHERE code_mode_until IS NOT NULL AND code_mode_until <= ?
                ORDER BY code_mode_until ASC LIMIT ?
                """, MAPPER, now, Math.max(1, Math.min(limit, 100)));
    }

    private static Conversation map(ResultSet rs, int rowNum) throws SQLException {
        Long codeUntil = rs.getObject("code_mode_until", Long.class);
        return new Conversation(
                rs.getString("conversation_key"),
                rs.getString("platform"),
                ChatType.valueOf(rs.getString("chat_type")),
                rs.getString("chat_id"),
                rs.getString("display_name"),
                rs.getString("opencode_session_id"),
                rs.getString("model_provider"),
                rs.getString("model_id"),
                rs.getString("model_variant"),
                rs.getString("agent_profile"),
                codeUntil,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
