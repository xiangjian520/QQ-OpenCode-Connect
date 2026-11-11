package com.xiangjian.qqbot.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OneBotEventParser {
    public Optional<InboundMessage> parse(JsonNode root) {
        if (!"message".equals(root.path("post_type").asText())) {
            return Optional.empty();
        }
        String messageType = root.path("message_type").asText();
        ChatType chatType = switch (messageType) {
            case "group" -> ChatType.GROUP;
            case "private" -> ChatType.PRIVATE;
            default -> null;
        };
        if (chatType == null) {
            return Optional.empty();
        }

        String selfId = root.path("self_id").asText();
        String userId = root.path("user_id").asText();
        String chatId = chatType == ChatType.GROUP ? root.path("group_id").asText() : userId;
        if (userId.isBlank() || chatId.isBlank()) {
            return Optional.empty();
        }

        JsonNode sender = root.path("sender");
        String displayName = firstNonBlank(
                sender.path("card").asText(),
                sender.path("nickname").asText(),
                userId
        );

        ParsedContent content = parseContent(root.path("message"), selfId);
        String text = content.text().strip();
        if (text.isBlank() && !root.path("raw_message").asText("").isBlank()) {
            text = root.path("raw_message").asText().strip();
        }

        return Optional.of(new InboundMessage(
                "onebot-v11",
                chatType,
                chatId,
                userId,
                displayName,
                text,
                content.mentionedSelf(),
                root.path("message_id").asText(Long.toString(System.currentTimeMillis())),
                root.path("time").asLong(System.currentTimeMillis() / 1000) * 1000
        ));
    }

    private ParsedContent parseContent(JsonNode message, String selfId) {
        if (message.isTextual()) {
            return new ParsedContent(message.asText(), false);
        }
        StringBuilder text = new StringBuilder();
        boolean mentioned = false;
        if (message.isArray()) {
            for (JsonNode segment : message) {
                String type = segment.path("type").asText();
                JsonNode data = segment.path("data");
                if ("text".equals(type)) {
                    text.append(data.path("text").asText(""));
                } else if ("at".equals(type)) {
                    String qq = data.path("qq").asText("");
                    if (!qq.isBlank() && qq.equals(selfId)) {
                        mentioned = true;
                        text.append(' ');
                    }
                } else if ("face".equals(type)) {
                    text.append(' ');
                }
            }
        }
        return new ParsedContent(text.toString(), mentioned);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record ParsedContent(String text, boolean mentionedSelf) {}
}
