package com.xiangjian.qqbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qqbot")
public record AppProperties(
        Web web,
        OpenCode opencode,
        OneBot onebot,
        Queue queue,
        Reply reply,
        Retention retention
) {
    public record Web(String adminUsername, String adminPassword) {}

    public record OpenCode(
            String mode,
            String baseUrl,
            String username,
            String password,
            String modelVariant,
            int timeoutSeconds
    ) {}

    public record OneBot(
            String token,
            boolean requireMention,
            String allowedPrivateIds,
            String allowedGroupIds,
            String adminQqIds
    ) {}

    public record Queue(long pollMs, int max) {}

    public record Reply(int chunkSize, long delayMs) {}

    public record Retention(int days) {}
}
