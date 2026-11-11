package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.opencode.OpenCodeClient;
import com.xiangjian.qqbot.opencode.OpenCodeModels;
import com.xiangjian.qqbot.repository.OpenCodeCustomModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OpenCodeCustomModelService {
    private static final Logger log = LoggerFactory.getLogger(OpenCodeCustomModelService.class);
    private static final List<String> SUPPORTED_VARIANTS = List.of(
            "none", "minimal", "low", "medium", "high", "xhigh", "max"
    );
    private static final Set<String> VARIANT_SET = Set.copyOf(SUPPORTED_VARIANTS);

    private final OpenCodeClient openCode;
    private final OpenCodeCustomModelRepository repository;

    public OpenCodeCustomModelService(OpenCodeClient openCode, OpenCodeCustomModelRepository repository) {
        this.openCode = openCode;
        this.repository = repository;
    }

    public List<OpenCodeModels.Model> listAll() {
        Map<String, OpenCodeModels.Model> merged = new LinkedHashMap<>();
        try {
            for (OpenCodeModels.Model model : openCode.listModels()) {
                merged.put(key(model.providerId(), model.id()), model);
            }
        } catch (RuntimeException ex) {
            log.debug("OpenCode model catalog unavailable, returning custom models only: {}", ex.getMessage());
        }
        for (OpenCodeCustomModelRepository.CustomModel model : repository.list()) {
            OpenCodeModels.Model custom = toModel(model);
            merged.put(key(custom.providerId(), custom.id()), custom);
        }
        return List.copyOf(merged.values());
    }

    public List<OpenCodeModels.Model> customModels() {
        return repository.list().stream().map(this::toModel).toList();
    }

    public Optional<OpenCodeModels.Model> find(String providerId, String modelId) {
        if (providerId == null || providerId.isBlank() || modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        return listAll().stream()
                .filter(model -> model.providerId().equals(providerId) && model.id().equals(modelId))
                .findFirst();
    }

    public OpenCodeModels.Model save(String providerId, String modelId, String displayName,
                                     List<String> requestedVariants) {
        String provider = required(providerId, "Provider ID", 96);
        String model = required(modelId, "Model ID", 255);
        String name = displayName == null || displayName.isBlank()
                ? provider + "/" + model : required(displayName, "显示名称", 255);
        List<String> variants = normalizeVariants(requestedVariants);
        repository.save(provider, model, name, String.join(",", variants));
        return toModel(new OpenCodeCustomModelRepository.CustomModel(
                provider, model, name, String.join(",", variants)));
    }

    public boolean delete(String providerId, String modelId) {
        return repository.delete(providerId, modelId) > 0;
    }

    public boolean isCustom(OpenCodeModels.Model model) {
        return model.raw() == null;
    }

    public Map<String, Object> view(OpenCodeModels.Model model) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", model.id());
        value.put("modelId", model.modelId());
        value.put("providerId", model.providerId());
        value.put("name", model.name());
        value.put("variants", model.variants());
        value.put("label", model.label());
        value.put("custom", isCustom(model));
        return value;
    }

    private OpenCodeModels.Model toModel(OpenCodeCustomModelRepository.CustomModel model) {
        List<String> variants = model.variants() == null || model.variants().isBlank()
                ? List.of() : Arrays.stream(model.variants().split(","))
                .map(String::strip).filter(value -> !value.isBlank()).toList();
        return new OpenCodeModels.Model(
                model.modelId(), model.modelId(), model.providerId(), model.displayName(), variants, null
        );
    }

    private List<String> normalizeVariants(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String item : requested) {
            if (item == null) continue;
            for (String variant : item.split("[,，\\s]+")) {
                String normalized = variant.strip().toLowerCase(Locale.ROOT);
                if (normalized.isBlank()) continue;
                if (!VARIANT_SET.contains(normalized)) {
                    throw new IllegalArgumentException("不支持的思考强度: " + variant);
                }
                selected.add(normalized);
            }
        }
        return SUPPORTED_VARIANTS.stream().filter(selected::contains).toList();
    }

    private static String required(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private static String key(String providerId, String modelId) {
        return providerId + "\u0000" + modelId;
    }
}
