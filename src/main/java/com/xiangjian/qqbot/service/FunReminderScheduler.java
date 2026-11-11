package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.OutboundMessage;
import com.xiangjian.qqbot.onebot.OneBotGateway;
import com.xiangjian.qqbot.repository.FunSocialRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.web.UiEventHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class FunReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(FunReminderScheduler.class);

    private final FunSocialRepository social;
    private final OneBotGateway oneBot;
    private final MessageRepository messages;
    private final UiEventHub events;

    public FunReminderScheduler(FunSocialRepository social, OneBotGateway oneBot,
                                MessageRepository messages, UiEventHub events) {
        this.social = social;
        this.oneBot = oneBot;
        this.messages = messages;
        this.events = events;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void deliverDueReminders() {
        for (FunSocialRepository.ReminderRow reminder : social.dueReminders(System.currentTimeMillis(), 50)) {
            String text = "⏰ 提醒：" + reminder.content();
            boolean sent;
            if (reminder.chatType() == ChatType.WEB) {
                String key = "web:WEB:admin";
                messages.insertOutbound(key, null, "system", text, "sent");
                events.publish("message", Map.of(
                        "conversationKey", key,
                        "direction", "OUT",
                        "content", text,
                        "status", "sent",
                        "time", Instant.now().toString()
                ));
                sent = true;
            } else {
                sent = oneBot.send(new OutboundMessage(
                        reminder.chatType(), reminder.chatId(), text, null
                ));
            }
            if (sent) {
                social.markReminderSent(reminder.id());
            } else {
                log.debug("Reminder {} could not be delivered; it will retry", reminder.id());
            }
        }
    }
}
