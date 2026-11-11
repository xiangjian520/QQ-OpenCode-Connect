package com.xiangjian.qqbot.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xiangjian.qqbot.opencode.OpenCodeClient;
import com.xiangjian.qqbot.opencode.OpenCodeModels;
import com.xiangjian.qqbot.repository.OpenCodeCustomModelRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenCodeCustomModelServiceTest {
    @Test
    void mergesBuiltInAndCustomModelsWithoutLosingCustomEntry() {
        OpenCodeClient openCode = mock(OpenCodeClient.class);
        OpenCodeCustomModelRepository repository = mock(OpenCodeCustomModelRepository.class);
        OpenCodeModels.Model builtIn = new OpenCodeModels.Model(
                "glm-5.3-flash-free", "glm-5.3-flash-free", "opencode", "GLM",
                List.of("none", "low"), JsonNodeFactory.instance.objectNode());
        when(openCode.listModels()).thenReturn(List.of(builtIn));
        when(repository.list()).thenReturn(List.of(new OpenCodeCustomModelRepository.CustomModel(
                "anthropic", "claude-sonnet-4", "Claude Sonnet", "none,low,medium")));
        OpenCodeCustomModelService service = new OpenCodeCustomModelService(openCode, repository);

        assertThat(service.listAll()).extracting(OpenCodeModels.Model::label)
                .containsExactly("opencode/glm-5.3-flash-free", "anthropic/claude-sonnet-4");
        assertThat(service.find("anthropic", "claude-sonnet-4")).isPresent()
                .get().extracting(OpenCodeModels.Model::name).isEqualTo("Claude Sonnet");
    }

    @Test
    void savesValidatedVariantsInStableOrder() {
        OpenCodeClient openCode = mock(OpenCodeClient.class);
        OpenCodeCustomModelRepository repository = mock(OpenCodeCustomModelRepository.class);
        when(repository.list()).thenReturn(List.of());
        OpenCodeCustomModelService service = new OpenCodeCustomModelService(openCode, repository);

        OpenCodeModels.Model model = service.save(
                " anthropic ", " claude-sonnet-4 ", " Claude Sonnet ",
                List.of("high,none", "low", "medium"));

        assertThat(model.label()).isEqualTo("anthropic/claude-sonnet-4");
        assertThat(model.variants()).containsExactly("none", "low", "medium", "high");
        verify(repository).save("anthropic", "claude-sonnet-4", "Claude Sonnet", "none,low,medium,high");
    }

    @Test
    void rejectsUnknownThinkingVariant() {
        OpenCodeCustomModelService service = new OpenCodeCustomModelService(
                mock(OpenCodeClient.class), mock(OpenCodeCustomModelRepository.class));

        assertThatThrownBy(() -> service.save("openrouter", "custom/model", "Custom", List.of("turbo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的思考强度");
    }
}
