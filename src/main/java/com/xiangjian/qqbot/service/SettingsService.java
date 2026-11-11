package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.config.AppProperties;
import com.xiangjian.qqbot.repository.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SettingsService {
    public static final String ALLOWED_PRIVATE_IDS = "allowedPrivateIds";
    public static final String ALLOWED_GROUP_IDS = "allowedGroupIds";
    public static final String ADMIN_QQ_IDS = "adminQqIds";
    public static final String REQUIRE_MENTION = "requireMention";
    public static final String QUEUE_MAX = "queueMax";
    public static final String REPLY_CHUNK_SIZE = "replyChunkSize";
    public static final String REPLY_DELAY_MS = "replyDelayMs";
    public static final String RETENTION_DAYS = "retentionDays";
    public static final String DEFAULT_VARIANT = "defaultVariant";

    private static final Set<String> EDITABLE = Set.of(
            ALLOWED_PRIVATE_IDS, ALLOWED_GROUP_IDS, ADMIN_QQ_IDS, REQUIRE_MENTION,
            QUEUE_MAX, REPLY_CHUNK_SIZE, REPLY_DELAY_MS, RETENTION_DAYS, DEFAULT_VARIANT
    );

    private final SettingsRepository repository;
    private final AppProperties properties;

    public SettingsService(SettingsRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public Set<String> allowedPrivateIds() {
        return ids(ALLOWED_PRIVATE_IDS, properties.onebot().allowedPrivateIds());
    }

    public Set<String> allowedGroupIds() {
        return ids(ALLOWED_GROUP_IDS, properties.onebot().allowedGroupIds());
    }

    public Set<String> adminQqIds() {
        return ids(ADMIN_QQ_IDS, properties.onebot().adminQqIds());
    }

    public boolean requireMention() {
        return bool(REQUIRE_MENTION, properties.onebot().requireMention());
    }

    public int queueMax() {
        return number(QUEUE_MAX, properties.queue().max(), 1, 50);
    }

    public int replyChunkSize() {
        return number(REPLY_CHUNK_SIZE, properties.reply().chunkSize(), 200, 4000);
    }

    public long replyDelayMs() {
        return number(REPLY_DELAY_MS, (int) properties.reply().delayMs(), 0, 5000);
    }

    public int retentionDays() {
        return number(RETENTION_DAYS, properties.retention().days(), 1, 3650);
    }

    public String defaultVariant() {
        return value(DEFAULT_VARIANT, properties.opencode().modelVariant());
    }

    public Map<String, String> effective() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(ALLOWED_PRIVATE_IDS, String.join(",", allowedPrivateIds()));
        result.put(ALLOWED_GROUP_IDS, String.join(",", allowedGroupIds()));
        result.put(ADMIN_QQ_IDS, String.join(",", adminQqIds()));
        result.put(REQUIRE_MENTION, Boolean.toString(requireMention()));
        result.put(QUEUE_MAX, Integer.toString(queueMax()));
        result.put(REPLY_CHUNK_SIZE, Integer.toString(replyChunkSize()));
        result.put(REPLY_DELAY_MS, Long.toString(replyDelayMs()));
        result.put(RETENTION_DAYS, Integer.toString(retentionDays()));
        result.put(DEFAULT_VARIANT, defaultVariant());
        return result;
    }

    public Map<String, String> update(Map<String, String> requested) {
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            if (!EDITABLE.contains(entry.getKey())) {
                throw new IllegalArgumentException("不支持修改设置: " + entry.getKey());
            }
            String normalized = normalize(entry.getKey(), entry.getValue());
            repository.put(entry.getKey(), normalized);
        }
        return effective();
    }

    private String value(String key, String fallback) {
        String stored = repository.all().get(key);
        return stored == null || stored.isBlank() ? fallback : stored;
    }

    private Set<String> ids(String key, String fallback) {
        return Arrays.stream(value(key, fallback).split("[,，\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean bool(String key, boolean fallback) {
        String stored = repository.all().get(key);
        return stored == null ? fallback : Boolean.parseBoolean(stored);
    }

    private int number(String key, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value(key, Integer.toString(fallback)));
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String normalize(String key, String value) {
        if (value == null) {
            throw new IllegalArgumentException("设置值不能为空: " + key);
        }
        String trimmed = value.trim();
        if (key.equals(REQUIRE_MENTION)) {
            return Boolean.toString(Boolean.parseBoolean(trimmed));
        }
        if (key.equals(QUEUE_MAX) || key.equals(REPLY_CHUNK_SIZE) || key.equals(REPLY_DELAY_MS) || key.equals(RETENTION_DAYS)) {
            Integer.parseInt(trimmed);
            return trimmed;
        }
        if (key.equals(DEFAULT_VARIANT)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }
}
