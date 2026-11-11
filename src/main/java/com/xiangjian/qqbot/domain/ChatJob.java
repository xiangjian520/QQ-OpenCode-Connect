package com.xiangjian.qqbot.domain;

public record ChatJob(
        long id,
        String conversationKey,
        String inboundMessageId,
        String text,
        String status,
        int attempts,
        String error,
        long createdAt,
        Long startedAt,
        Long completedAt
) {}
