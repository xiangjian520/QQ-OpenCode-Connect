package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.config.AppProperties;
import com.xiangjian.qqbot.repository.SettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {
    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Web("admin", "password"),
                new AppProperties.OpenCode("docker", "http://localhost", "opencode", "", "none", 20),
                new AppProperties.OneBot("", true, "", "", ""),
                new AppProperties.Queue(500, 5),
                new AppProperties.Reply(1800, 0),
                new AppProperties.Retention(90)
        );
    }

    @Test
    void parsesCsvIdsAndClampsNumbers() {
        SettingsRepository repository = mock(SettingsRepository.class);
        Map<String, String> stored = new HashMap<>();
        stored.put(SettingsService.ADMIN_QQ_IDS, "1, 2，3");
        stored.put(SettingsService.QUEUE_MAX, "999");
        when(repository.all()).thenReturn(stored);
        SettingsService service = new SettingsService(repository, properties());
        assertThat(service.adminQqIds()).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(service.queueMax()).isEqualTo(50);
    }

    @Test
    void masksOneBotTokenAndDoesNotOverwriteItWithBlankInput() {
        SettingsRepository repository = mock(SettingsRepository.class);
        Map<String, String> stored = new HashMap<>();
        stored.put(SettingsService.ONEBOT_TOKEN, "1234567890abcdef");
        when(repository.all()).thenReturn(stored);
        SettingsService service = new SettingsService(repository, properties());

        assertThat(service.oneBotTokenPreview()).isEqualTo("1234••••••cdef");
        service.updateOneBot(Map.of(SettingsService.ONEBOT_TOKEN, "  "));

        verify(repository, never()).put(anyString(), anyString());
    }

    @Test
    void persistsAndReadsOpenCodeDefaults() {
        SettingsRepository repository = mock(SettingsRepository.class);
        Map<String, String> stored = new HashMap<>();
        when(repository.all()).thenReturn(stored);
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(repository).put(anyString(), anyString());
        SettingsService service = new SettingsService(repository, properties());

        service.updateOpenCodeDefaults(" anthropic ", " claude-sonnet-4 ", " LOW ");

        assertThat(service.openCodeSettings()).containsEntry("providerId", "anthropic")
                .containsEntry("modelId", "claude-sonnet-4")
                .containsEntry("variant", "low");
    }

    @Test
    void regeneratesAStrongOneBotToken() {
        SettingsRepository repository = mock(SettingsRepository.class);
        when(repository.all()).thenReturn(new HashMap<>());
        SettingsService service = new SettingsService(repository, properties());

        String token = service.regenerateOneBotToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        verify(repository).put(SettingsService.ONEBOT_TOKEN, token);
    }
}
