package com.xiangjian.qqbot.domain;

public record OutboundMessage(
        ChatType chatType,
        String chatId,
        String text,
        String replyToMessageId
) {}
