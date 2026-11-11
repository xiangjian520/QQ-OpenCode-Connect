package com.xiangjian.qqbot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandParserTest {
    @Test
    void parsesChineseCommandAndArguments() {
        var command = CommandParser.parse("/思考 关闭").orElseThrow();
        assertThat(command.name()).isEqualTo("思考");
        assertThat(command.args()).isEqualTo("关闭");
    }

    @Test
    void ignoresMentionTextBeforeCommand() {
        assertThat(CommandParser.parse("@樱花 /状态@123456")).isEmpty();
    }

    @Test
    void parsesCommandWithBotMentionInsidePrefixShape() {
        var command = CommandParser.parse("/状态@123456").orElseThrow();
        assertThat(command.name()).isEqualTo("状态");
        assertThat(command.args()).isEmpty();
    }

    @Test
    void ignoresPlainText() {
        assertThat(CommandParser.parse("你好")).isEmpty();
    }

    @Test
    void marksMalformedSlashCommandAsUnknown() {
        var command = CommandParser.parse("/ ").orElseThrow();
        assertThat(command.name()).isEqualTo("未知");
    }
}
