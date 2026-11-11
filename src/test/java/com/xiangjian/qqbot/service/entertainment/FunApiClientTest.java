package com.xiangjian.qqbot.service.entertainment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FunApiClientTest {
    @Test
    void resolvesEmojiKitchenCombinationFromBundledMetadata() {
        FunApiClient client = new FunApiClient(new ObjectMapper());

        assertThat(client.emojiMixUrl("😀", "🐱"))
                .contains("https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f431.png");
        assertThat(client.emojiMixUrl("🐱", "😀")).isPresent();
        assertThat(client.emojiMixUrl("不是表情", "🐱")).isEmpty();
    }
}
