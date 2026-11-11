package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.opencode.OpenCodeClient;
import com.xiangjian.qqbot.repository.ConversationRepository;
import com.xiangjian.qqbot.web.UiEventHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CodingModeService {
    private static final Logger log = LoggerFactory.getLogger(CodingModeService.class);

    private final ConversationRepository conversations;
    private final OpenCodeClient openCode;
    private final UiEventHub events;

    public CodingModeService(ConversationRepository conversations, OpenCodeClient openCode, UiEventHub events) {
        this.conversations = conversations;
        this.openCode = openCode;
        this.events = events;
    }

    @Scheduled(fixedDelay = 30_000)
    public void disableExpiredModes() {
        for (Conversation conversation : conversations.findExpiredCodeModes(System.currentTimeMillis(), 100)) {
            conversations.updateAgent(conversation.conversationKey(), "qq-chat", null);
            if (conversation.opencodeSessionId() != null) {
                try {
                    openCode.switchAgent(conversation.opencodeSessionId(), "qq-chat");
                } catch (RuntimeException ex) {
                    log.debug("Could not switch expired session agent: {}", ex.getMessage());
                }
            }
            events.publish("status-changed", Map.of(
                    "reason", "code-mode-expired",
                    "conversationKey", conversation.conversationKey()
            ));
            events.log("INFO", "编码模式已到期: " + conversation.conversationKey());
        }
    }
}
