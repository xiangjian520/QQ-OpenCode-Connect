package com.xiangjian.qqbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.domain.InboundMessage;
import com.xiangjian.qqbot.repository.EntertainmentGameRepository;
import com.xiangjian.qqbot.repository.FunSocialRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.service.entertainment.FunApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntertainmentServiceTest {
    @Test
    void todayWifeIsASafeFictionalPersona() {
        SettingsService settings = mock(SettingsService.class);
        EntertainmentGameRepository games = mock(EntertainmentGameRepository.class);
        FunSocialRepository social = mock(FunSocialRepository.class);
        FunApiClient api = mock(FunApiClient.class);
        MessageRepository messages = mock(MessageRepository.class);
        CommandService commands = mock(CommandService.class);
        when(settings.entertainmentEnabled()).thenReturn(true);
        EntertainmentService service = new EntertainmentService(
                settings, mock(PermissionService.class), games, social, api, messages, commands, new ObjectMapper());
        InboundMessage message = message("/今日老婆");

        boolean handled = service.tryHandle(message, conversation());

        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(commands).reply(eq(message), reply.capture());
        assertThat(handled).isTrue();
        assertThat(reply.getValue()).startsWith("今日老婆：")
                .contains("拒绝任何成人内容")
                .doesNotContainIgnoringCase("nsfw", "色情", "露骨", "裸");
    }

    @Test
    void overviewExposesOnlyBundledSafeGalleryAssets() {
        SettingsService settings = mock(SettingsService.class);
        EntertainmentGameRepository games = mock(EntertainmentGameRepository.class);
        FunSocialRepository social = mock(FunSocialRepository.class);
        FunApiClient api = mock(FunApiClient.class);
        MessageRepository messages = mock(MessageRepository.class);
        CommandService commands = mock(CommandService.class);
        when(settings.entertainmentEnabled()).thenReturn(true);
        when(games.countActive()).thenReturn(0);
        when(games.recent(20)).thenReturn(List.of());
        when(social.poll(anyString())).thenReturn(Optional.empty());
        EntertainmentService service = new EntertainmentService(
                settings, mock(PermissionService.class), games, social, api, messages, commands, new ObjectMapper());

        Map<String, Object> overview = service.overview();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> gallery = (List<Map<String, String>>) overview.get("pigGallery");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tarot = (List<Map<String, String>>) overview.get("tarotGallery");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> stickers = (List<Map<String, String>>) overview.get("stickerGallery");

        assertThat(gallery).hasSize(14).allSatisfy(pig -> {
            assertThat(pig.get("image")).startsWith("/entertainment/pigs/");
            assertThat(new ClassPathResource("static" + pig.get("image")).exists()).isTrue();
        });
        assertThat(tarot).hasSize(22).allSatisfy(card ->
                assertThat(new ClassPathResource("static" + card.get("image")).exists()).isTrue());
        assertThat(stickers).hasSize(23).allSatisfy(sticker ->
                assertThat(new ClassPathResource("static" + sticker.get("image")).exists()).isTrue());
        assertThat(overview.get("featureCount")).isEqualTo(32);
    }

    private static InboundMessage message(String text) {
        return new InboundMessage(
                "web", ChatType.WEB, "admin", "web-admin", "网页管理员",
                text, false, "test-message", System.currentTimeMillis()
        );
    }

    private static Conversation conversation() {
        long now = System.currentTimeMillis();
        return new Conversation(
                "web:web:admin", "web", ChatType.WEB, "admin", "网页管理员",
                null, null, null, null, "qq-chat", null, now, now
        );
    }
}
