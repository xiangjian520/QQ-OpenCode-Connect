package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.config.AppProperties;
import com.xiangjian.qqbot.repository.SettingsRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SettingsService {
    public static final String ONEBOT_TOKEN = "onebotToken";
    public static final String ONEBOT_ALLOWED_PRIVATE_IDS = "onebotAllowedPrivateIds";
    public static final String ONEBOT_ALLOWED_GROUP_IDS = "onebotAllowedGroupIds";
    public static final String ONEBOT_ADMIN_QQ_IDS = "onebotAdminQqIds";
    public static final String ONEBOT_REQUIRE_MENTION = "onebotRequireMention";

    public static final String OPENCODE_DEFAULT_PROVIDER = "opencodeDefaultProvider";
    public static final String OPENCODE_DEFAULT_MODEL = "opencodeDefaultModel";
    public static final String OPENCODE_DEFAULT_VARIANT = "opencodeDefaultVariant";

    public static final String QUEUE_MAX = "queueMax";
    public static final String REPLY_CHUNK_SIZE = "replyChunkSize";
    public static final String REPLY_DELAY_MS = "replyDelayMs";
    public static final String RETENTION_DAYS = "retentionDays";
    public static final String ENTERTAINMENT_ENABLED = "entertainmentEnabled";

    // Compatibility aliases for callers and old settings.
    public static final String ALLOWED_PRIVATE_IDS = ONEBOT_ALLOWED_PRIVATE_IDS;
    public static final String ALLOWED_GROUP_IDS = ONEBOT_ALLOWED_GROUP_IDS;
    public static final String ADMIN_QQ_IDS = ONEBOT_ADMIN_QQ_IDS;
    public static final String REQUIRE_MENTION = ONEBOT_REQUIRE_MENTION;
    public static final String DEFAULT_VARIANT = OPENCODE_DEFAULT_VARIANT;

    private static final Set<String> BEHAVIOUR_EDITABLE = Set.of(
            QUEUE_MAX, REPLY_CHUNK_SIZE, REPLY_DELAY_MS, RETENTION_DAYS, ENTERTAINMENT_ENABLED
    );
    private static final Set<String> ONEBOT_EDITABLE = Set.of(
            ONEBOT_TOKEN, ONEBOT_ALLOWED_PRIVATE_IDS, ONEBOT_ALLOWED_GROUP_IDS,
            ONEBOT_ADMIN_QQ_IDS, ONEBOT_REQUIRE_MENTION
    );

    private final SettingsRepository repository;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SettingsService(SettingsRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public Set<String> allowedPrivateIds() {
        return ids(ONEBOT_ALLOWED_PRIVATE_IDS, "allowedPrivateIds", properties.onebot().allowedPrivateIds());
    }

    public Set<String> allowedGroupIds() {
        return ids(ONEBOT_ALLOWED_GROUP_IDS, "allowedGroupIds", properties.onebot().allowedGroupIds());
    }

    public Set<String> adminQqIds() {
        return ids(ONEBOT_ADMIN_QQ_IDS, "adminQqIds", properties.onebot().adminQqIds());
    }

    public boolean requireMention() {
        return bool(ONEBOT_REQUIRE_MENTION, "requireMention", properties.onebot().requireMention());
    }

    public String oneBotToken() {
        String stored = storedValue(ONEBOT_TOKEN, null);
        return stored == null || stored.isBlank() ? properties.onebot().token() : stored;
    }

    public boolean oneBotTokenConfigured() {
        String token = oneBotToken();
        return token != null && !token.isBlank();
    }

    public String oneBotTokenPreview() {
        return mask(oneBotToken());
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

    public boolean entertainmentEnabled() {
        return bool(ENTERTAINMENT_ENABLED, null, true);
    }

    public String defaultValueVariant() {
        return value(OPENCODE_DEFAULT_VARIANT, null, properties.opencode().modelVariant());
    }

    @Deprecated
    public String defaultVariant() {
        return defaultValueVariant();
    }

    public String defaultProviderId() {
        return value(OPENCODE_DEFAULT_PROVIDER, null, "");
    }

    public String defaultModelId() {
        return value(OPENCODE_DEFAULT_MODEL, null, "");
    }

    public Map<String, String> effective() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(QUEUE_MAX, Integer.toString(queueMax()));
        result.put(REPLY_CHUNK_SIZE, Integer.toString(replyChunkSize()));
        result.put(REPLY_DELAY_MS, Long.toString(replyDelayMs()));
        result.put(RETENTION_DAYS, Integer.toString(retentionDays()));
        result.put(ENTERTAINMENT_ENABLED, Boolean.toString(entertainmentEnabled()));
        return result;
    }

    public Map<String, String> update(Map<String, String> requested) {
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            if (!BEHAVIOUR_EDITABLE.contains(entry.getKey())) {
                throw new IllegalArgumentException("不支持修改运行设置: " + entry.getKey());
            }
            repository.put(entry.getKey(), normalize(entry.getKey(), entry.getValue()));
        }
        return effective();
    }

    public Map<String, Object> oneBotSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokenConfigured", oneBotTokenConfigured());
        result.put("tokenPreview", oneBotTokenPreview());
        result.put("requireMention", requireMention());
        result.put("allowedPrivateIds", String.join(",", allowedPrivateIds()));
        result.put("allowedGroupIds", String.join(",", allowedGroupIds()));
        result.put("adminQqIds", String.join(",", adminQqIds()));
        result.put("websocketPath", "/onebot/v11/ws");
        return result;
    }

    public Map<String, Object> updateOneBot(Map<String, String> requested) {
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            if (!ONEBOT_EDITABLE.contains(entry.getKey())) {
                throw new IllegalArgumentException("不支持修改 OneBot 设置: " + entry.getKey());
            }
            if (ONEBOT_TOKEN.equals(entry.getKey()) && (entry.getValue() == null || entry.getValue().isBlank())) {
                continue;
            }
            repository.put(entry.getKey(), normalize(entry.getKey(), entry.getValue()));
        }
        return oneBotSettings();
    }

    public String regenerateOneBotToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.put(ONEBOT_TOKEN, token);
        return token;
    }

    public Map<String, Object> openCodeSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerId", defaultProviderId());
        result.put("modelId", defaultModelId());
        result.put("variant", defaultValueVariant());
        return result;
    }

    public Map<String, Object> updateOpenCodeDefaults(String providerId, String modelId, String variant) {
        String provider = providerId == null ? "" : providerId.strip();
        String model = modelId == null ? "" : modelId.strip();
        if (provider.isBlank() != model.isBlank()) {
            throw new IllegalArgumentException("模型 Provider 和模型 ID 必须同时填写或同时留空");
        }
        String selectedVariant = variant == null ? "" : variant.strip().toLowerCase(Locale.ROOT);
        repository.put(OPENCODE_DEFAULT_PROVIDER, provider);
        repository.put(OPENCODE_DEFAULT_MODEL, model);
        repository.put(OPENCODE_DEFAULT_VARIANT, selectedVariant);
        return openCodeSettings();
    }

    private String value(String primary, String legacy, String fallback) {
        String stored = storedValue(primary, legacy);
        return stored == null || stored.isBlank() ? fallback : stored;
    }

    private String storedValue(String primary, String legacy) {
        Map<String, String> all = repository.all();
        String stored = all.get(primary);
        if ((stored == null || stored.isBlank()) && legacy != null) {
            stored = all.get(legacy);
        }
        return stored;
    }

    private Set<String> ids(String primary, String legacy, String fallback) {
        return Arrays.stream(value(primary, legacy, fallback).split("[,，\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean bool(String primary, String legacy, boolean fallback) {
        String stored = storedValue(primary, legacy);
        return stored == null ? fallback : Boolean.parseBoolean(stored);
    }

    private int number(String key, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value(key, null, Integer.toString(fallback)));
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
        if (key.equals(ONEBOT_REQUIRE_MENTION) || key.equals(ENTERTAINMENT_ENABLED)) {
            return Boolean.toString(Boolean.parseBoolean(trimmed));
        }
        if (key.equals(QUEUE_MAX) || key.equals(REPLY_CHUNK_SIZE)
                || key.equals(REPLY_DELAY_MS) || key.equals(RETENTION_DAYS)) {
            Integer.parseInt(trimmed);
            return trimmed;
        }
        if (key.equals(OPENCODE_DEFAULT_VARIANT)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 10) {
            return "••••••";
        }
        return value.substring(0, 4) + "••••••" + value.substring(value.length() - 4);
    }
}
