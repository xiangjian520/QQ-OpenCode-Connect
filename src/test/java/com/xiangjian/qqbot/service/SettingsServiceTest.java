package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.config.AppProperties;
import com.xiangjian.qqbot.repository.SettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {
    @Test
    void parsesCsvIdsAndClampsNumbers() {
        SettingsRepository repository = mock(SettingsRepository.class);
        Map<String, String> stored = new HashMap<>();
        stored.put(SettingsService.ADMIN_QQ_IDS, "1, 2，3");
        stored.put(SettingsService.QUEUE_MAX, "999");
        when(repository.all()).thenReturn(stored);
        var properties = new AppProperties(
                new AppProperties.Web("admin", "password"),
                new AppProperties.OpenCode("docker", "http://localhost", "opencode", "", "none", 20),
                new AppProperties.OneBot("", true, "", "", ""),
                new AppProperties.Queue(500, 5),
                new AppProperties.Reply(1800, 0),
                new AppProperties.Retention(90)
        );
        SettingsService service = new SettingsService(repository, properties);
        assertThat(service.adminQqIds()).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(service.queueMax()).isEqualTo(50);
    }
}
