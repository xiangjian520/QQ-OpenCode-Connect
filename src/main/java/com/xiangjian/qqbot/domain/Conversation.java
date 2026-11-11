package com.xiangjian.qqbot.domain;

public record Conversation(
        String conversationKey,
        String platform,
        ChatType chatType,
        String chatId,
        String displayName,
        String opencodeSessionId,
        String modelProvider,
        String modelId,
        String modelVariant,
        String agentProfile,
        Long codeModeUntil,
        long createdAt,
        long updatedAt
) {
    public boolean codeModeActive(long now) {
        return codeModeUntil != null && codeModeUntil > now;
    }
}
