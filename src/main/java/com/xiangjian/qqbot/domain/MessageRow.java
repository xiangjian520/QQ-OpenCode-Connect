package com.xiangjian.qqbot.domain;

public record MessageRow(
        long id,
        String conversationKey,
        String direction,
        String platformMessageId,
        String opencodeMessageId,
        String senderId,
        String content,
        String status,
        long createdAt
) {}
