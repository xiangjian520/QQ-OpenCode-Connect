package com.xiangjian.qqbot.domain;

public record InboundMessage(
        String platform,
        ChatType chatType,
        String chatId,
        String userId,
        String displayName,
        String text,
        boolean mentionedBot,
        String messageId,
        long timestamp
) {
    public String conversationKey() {
        return platform + ":" + chatType.name().toLowerCase() + ":" + chatId;
    }

    public boolean isPrivate() {
        return chatType == ChatType.PRIVATE;
    }

    public boolean isGroup() {
        return chatType == ChatType.GROUP;
    }
}
