package com.xiangjian.qqbot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSplitterTest {
    private final MessageSplitter splitter = new MessageSplitter();

    @Test
    void keepsShortTextIntact() {
        assertThat(splitter.split("你好，世界", 100)).containsExactly("你好，世界");
    }

    @Test
    void splitsOnParagraphBoundary() {
        String first = "甲".repeat(150) + "\n\n";
        String text = first + "乙".repeat(150);
        var chunks = splitter.split(text, 200);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst()).endsWith("甲");
        assertThat(chunks.getLast()).startsWith("乙");
    }

    @Test
    void neverDropsText() {
        String text = "abcdef".repeat(120);
        String joined = String.join("", splitter.split(text, 200));
        assertThat(joined).isEqualTo(text);
    }
}
