package com.xiangjian.qqbot.opencode;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class OpenCodeModels {
    private OpenCodeModels() {}

    public record Info(boolean healthy, String version, String error) {}

    public record Model(
            String id,
            String modelId,
            String providerId,
            String name,
            List<String> variants,
            JsonNode raw
    ) {
        public String label() {
            return providerId + "/" + modelId;
        }

        public boolean supports(String variant) {
            return variant != null && variants.contains(variant);
        }

        public boolean enabledOrAssumed() {
            JsonNode enabled = raw == null ? null : raw.get("enabled");
            return enabled == null || enabled.isNull() || enabled.asBoolean(true);
        }
    }

    public record ModelRef(String id, String providerID, String variant) {}

    public record AssistantReply(String messageId, String text) {}
}
