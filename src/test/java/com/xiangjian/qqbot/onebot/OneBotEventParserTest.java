package com.xiangjian.qqbot.onebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OneBotEventParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OneBotEventParser parser = new OneBotEventParser();

    @Test
    void parsesGroupMessageAndMention() throws Exception {
        var root = mapper.readTree("""
                {
                  "post_type": "message",
                  "message_type": "group",
                  "time": 1762820000,
                  "self_id": 10001,
                  "group_id": 20002,
                  "user_id": 30003,
                  "message_id": 42,
                  "sender": {"nickname": "测试用户", "card": "春日"},
                  "message": [
                    {"type": "at", "data": {"qq": "10001"}},
                    {"type": "text", "data": {"text": " /帮助"}}
                  ]
                }
                """);
        var message = parser.parse(root).orElseThrow();
        assertThat(message.chatType().name()).isEqualTo("GROUP");
        assertThat(message.chatId()).isEqualTo("20002");
        assertThat(message.userId()).isEqualTo("30003");
        assertThat(message.displayName()).isEqualTo("春日");
        assertThat(message.mentionedBot()).isTrue();
        assertThat(message.text()).isEqualTo("/帮助");
    }

    @Test
    void parsesPrivateStringMessage() throws Exception {
        var root = mapper.readTree("""
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": 10001,
                  "user_id": 30003,
                  "message_id": "abc",
                  "message": "你好"
                }
                """);
        var message = parser.parse(root).orElseThrow();
        assertThat(message.chatType().name()).isEqualTo("PRIVATE");
        assertThat(message.conversationKey()).isEqualTo("onebot-v11:private:30003");
        assertThat(message.text()).isEqualTo("你好");
    }

    @Test
    void ignoresNonMessageEvents() throws Exception {
        assertThat(parser.parse(mapper.readTree("{\"post_type\":\"notice\"}"))).isEmpty();
    }
}
