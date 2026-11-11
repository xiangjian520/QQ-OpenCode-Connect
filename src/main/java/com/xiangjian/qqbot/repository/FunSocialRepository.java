package com.xiangjian.qqbot.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangjian.qqbot.domain.ChatType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FunSocialRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<LinkedHashMap<String, Integer>> VOTE_MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FunSocialRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UserStats stats(String platform, String userId) {
        List<UserStats> rows = jdbc.query("""
                SELECT display_name, points, sign_streak, signed_days, last_sign_day
                FROM fun_user_stats WHERE platform = ? AND user_id = ?
                """, (rs, rowNum) -> new UserStats(
                rs.getString("display_name"),
                rs.getInt("points"),
                rs.getInt("sign_streak"),
                rs.getInt("signed_days"),
                rs.getString("last_sign_day")
        ), platform, userId);
        return rows.isEmpty() ? new UserStats("", 0, 0, 0, null) : rows.getFirst();
    }

    @Transactional
    public SignInResult signIn(String platform, String userId, String displayName, LocalDate today) {
        UserStats current = stats(platform, userId);
        String day = today.toString();
        if (day.equals(current.lastSignDay())) {
            return new SignInResult(false, 0, current);
        }
        int streak = today.minusDays(1).toString().equals(current.lastSignDay())
                ? current.signStreak() + 1 : 1;
        int reward = 10 + Math.min(streak, 7) * 2;
        int points = current.points() + reward;
        int signedDays = current.signedDays() + 1;
        String name = displayName == null || displayName.isBlank() ? current.displayName() : displayName;
        jdbc.update("""
                INSERT INTO fun_user_stats
                    (platform, user_id, display_name, points, sign_streak, signed_days, last_sign_day, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), points = VALUES(points),
                    sign_streak = VALUES(sign_streak), signed_days = VALUES(signed_days),
                    last_sign_day = VALUES(last_sign_day), updated_at = VALUES(updated_at)
                """, platform, userId, name, points, streak, signedDays, day, System.currentTimeMillis());
        return new SignInResult(true, reward,
                new UserStats(name, points, streak, signedDays, day));
    }

    public List<LeaderboardRow> leaderboard(int limit) {
        return jdbc.query("""
                SELECT display_name, user_id, points, sign_streak, signed_days
                FROM fun_user_stats ORDER BY points DESC, signed_days DESC, updated_at ASC LIMIT ?
                """, (rs, rowNum) -> new LeaderboardRow(
                rs.getString("display_name"),
                rs.getString("user_id"),
                rs.getInt("points"),
                rs.getInt("sign_streak"),
                rs.getInt("signed_days")
        ), Math.max(1, Math.min(limit, 50)));
    }

    @Transactional
    public long addReminder(String platform, ChatType chatType, String chatId, String userId,
                            String content, long dueAt) {
        jdbc.update("""
                INSERT INTO fun_reminders
                    (platform, chat_type, chat_id, user_id, content, due_at, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, platform, chatType.name(), chatId, userId, content, dueAt, System.currentTimeMillis());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    public List<ReminderRow> dueReminders(long now, int limit) {
        return jdbc.query("""
                SELECT * FROM fun_reminders
                WHERE status = 'PENDING' AND due_at <= ?
                ORDER BY due_at ASC LIMIT ?
                """, (rs, rowNum) -> new ReminderRow(
                rs.getLong("id"),
                rs.getString("platform"),
                ChatType.valueOf(rs.getString("chat_type")),
                rs.getString("chat_id"),
                rs.getString("user_id"),
                rs.getString("content"),
                rs.getLong("due_at")
        ), now, Math.max(1, Math.min(limit, 100)));
    }

    public void markReminderSent(long id) {
        jdbc.update("UPDATE fun_reminders SET status = 'SENT', sent_at = ? WHERE id = ? AND status = 'PENDING'",
                System.currentTimeMillis(), id);
    }

    public void savePoll(String conversationKey, String question, List<String> options,
                         Map<String, Integer> votes, String createdBy) {
        long now = System.currentTimeMillis();
        try {
            jdbc.update("""
                    INSERT INTO fun_polls
                        (conversation_key, question, options_json, votes_json, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE question = VALUES(question), options_json = VALUES(options_json),
                        votes_json = VALUES(votes_json), created_by = VALUES(created_by), updated_at = VALUES(updated_at)
                    """, conversationKey, question, objectMapper.writeValueAsString(options),
                    objectMapper.writeValueAsString(votes), createdBy, now, now);
        } catch (Exception ex) {
            throw new IllegalStateException("保存投票失败", ex);
        }
    }

    public Optional<Poll> poll(String conversationKey) {
        List<Poll> rows = jdbc.query("""
                SELECT question, options_json, votes_json, created_by
                FROM fun_polls WHERE conversation_key = ?
                """, (rs, rowNum) -> new Poll(
                rs.getString("question"),
                read(rs.getString("options_json"), STRING_LIST),
                read(rs.getString("votes_json"), VOTE_MAP),
                rs.getString("created_by")
        ), conversationKey);
        return rows.stream().findFirst();
    }

    @Transactional
    public Poll vote(String conversationKey, String userId, int optionIndex) {
        Poll current = poll(conversationKey).orElseThrow(() -> new IllegalStateException("当前没有进行中的投票"));
        if (optionIndex < 1 || optionIndex > current.options().size()) {
            throw new IllegalArgumentException("选项编号需要在 1-" + current.options().size() + " 之间");
        }
        LinkedHashMap<String, Integer> votes = new LinkedHashMap<>(current.votes());
        votes.put(userId, optionIndex);
        savePoll(conversationKey, current.question(), current.options(), votes, current.createdBy());
        return new Poll(current.question(), current.options(), votes, current.createdBy());
    }

    public void deletePoll(String conversationKey) {
        jdbc.update("DELETE FROM fun_polls WHERE conversation_key = ?", conversationKey);
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("读取投票数据失败", ex);
        }
    }

    public record UserStats(String displayName, int points, int signStreak, int signedDays, String lastSignDay) {}
    public record SignInResult(boolean firstToday, int reward, UserStats stats) {}
    public record LeaderboardRow(String displayName, String userId, int points, int signStreak, int signedDays) {}
    public record ReminderRow(long id, String platform, ChatType chatType, String chatId, String userId,
                              String content, long dueAt) {}
    public record Poll(String question, List<String> options, Map<String, Integer> votes, String createdBy) {}
}
