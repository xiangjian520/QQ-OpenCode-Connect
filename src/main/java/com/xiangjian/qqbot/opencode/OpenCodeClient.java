package com.xiangjian.qqbot.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiangjian.qqbot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class OpenCodeClient {
    private static final Logger log = LoggerFactory.getLogger(OpenCodeClient.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public OpenCodeClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(properties.opencode().baseUrl());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public OpenCodeModels.Info info() {
        try {
            JsonNode root = get("/api/info");
            return new OpenCodeModels.Info(true, root.path("version").asText("unknown"), "");
        } catch (RuntimeException ex) {
            return new OpenCodeModels.Info(false, "unknown", rootMessage(ex));
        }
    }

    public Optional<OpenCodeModels.Model> defaultModel() {
        JsonNode root = get("/api/model/default");
        JsonNode data = root.path("data");
        return data.isObject() ? Optional.of(parseModel(data)) : Optional.empty();
    }

    public List<OpenCodeModels.Model> listModels() {
        JsonNode root = get("/api/model");
        List<OpenCodeModels.Model> result = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            if (item.isObject()) {
                result.add(parseModel(item));
            }
        }
        return result;
    }

    public OpenCodeModels.ModelRef resolveModel(String provider, String modelId, String variant) {
        if (provider != null && !provider.isBlank() && modelId != null && !modelId.isBlank()) {
            return new OpenCodeModels.ModelRef(modelId, provider, normalizeVariant(variant));
        }
        OpenCodeModels.Model model = defaultModel()
                .orElseThrow(() -> new OpenCodeException("OpenCode 尚未配置可用模型，请先执行 opencode auth login", 401));
        String selectedVariant = model.supports(variant) ? variant : null;
        return new OpenCodeModels.ModelRef(model.id(), model.providerId(), selectedVariant);
    }

    public String createSession(String title, String agent, OpenCodeModels.ModelRef model) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        if (agent != null && !agent.isBlank()) {
            body.put("agent", agent);
        }
        if (model != null) {
            ObjectNode modelNode = body.putObject("model");
            modelNode.put("id", model.id());
            modelNode.put("providerID", model.providerID());
            if (model.variant() != null && !model.variant().isBlank()) {
                modelNode.put("variant", model.variant());
            }
        }
        JsonNode root = post("/api/session", body);
        String id = root.path("data").path("id").asText();
        if (id.isBlank()) {
            throw new OpenCodeException("OpenCode 创建会话未返回 session id", -1);
        }
        return id;
    }

    public String prompt(String sessionId, String text, String conversationKey) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("source", "qq-opencode-connect");
        metadata.put("conversationKey", conversationKey);
        JsonNode root = post("/api/session/" + encode(sessionId) + "/prompt", body);
        return root.path("data").path("id").asText();
    }

    public void interrupt(String sessionId) {
        post("/api/session/" + encode(sessionId) + "/interrupt?resume=false", objectMapper.createObjectNode());
    }

    public void switchModel(String sessionId, OpenCodeModels.ModelRef model) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode modelNode = body.putObject("model");
        modelNode.put("id", model.id());
        modelNode.put("providerID", model.providerID());
        if (model.variant() != null && !model.variant().isBlank()) {
            modelNode.put("variant", model.variant());
        }
        post("/api/session/" + encode(sessionId) + "/model", body);
    }

    public void switchAgent(String sessionId, String agent) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("agent", agent);
        post("/api/session/" + encode(sessionId) + "/agent", body);
    }

    public long sessionIdleAt(String sessionId) {
        try {
            JsonNode root = get("/api/session/" + encode(sessionId));
            return root.path("data").path("time").path("idle").asLong(0);
        } catch (OpenCodeException ex) {
            if (ex.statusCode() == 404) {
                return 0;
            }
            throw ex;
        }
    }

    public Optional<OpenCodeModels.AssistantReply> latestAssistantReply(String sessionId, long promptStartedAt) {
        JsonNode root = get("/api/session/" + encode(sessionId) + "/message?type=assistant&order=desc&limit=20");
        for (JsonNode message : root.path("data")) {
            if (!"assistant".equals(message.path("type").asText())) {
                continue;
            }
            long created = message.path("time").path("created").asLong(0);
            if (created > 0 && created < promptStartedAt - 5_000) {
                continue;
            }
            String text = extractAssistantText(message);
            if (!text.isBlank()) {
                return Optional.of(new OpenCodeModels.AssistantReply(message.path("id").asText(), text));
            }
            JsonNode error = message.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new OpenCodeException("OpenCode 返回错误: " + error.toString(), -1);
            }
        }
        return Optional.empty();
    }

    public boolean deleteSession(String sessionId) {
        try {
            JsonNode root = delete("/api/session/" + encode(sessionId));
            return root.path("data").asBoolean(root.asBoolean(true));
        } catch (RuntimeException ex) {
            log.warn("Could not delete OpenCode session {}: {}", sessionId, ex.getMessage());
            return false;
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    private String extractAssistantText(JsonNode message) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : message.path("content")) {
            if ("text".equals(part.path("type").asText())) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part.path("text").asText());
            }
        }
        return text.toString().trim();
    }

    private OpenCodeModels.Model parseModel(JsonNode node) {
        List<String> variants = new ArrayList<>();
        for (JsonNode variant : node.path("variants")) {
            String id = variant.path("id").asText();
            if (!id.isBlank()) {
                variants.add(id);
            }
        }
        return new OpenCodeModels.Model(
                node.path("id").asText(),
                node.path("modelID").asText(),
                node.path("providerID").asText(),
                node.path("name").asText(node.path("modelID").asText()),
                List.copyOf(variants),
                node
        );
    }

    private JsonNode get(String path) {
        return send(request(path).GET().build());
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return send(request(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build());
        } catch (IOException ex) {
            throw new OpenCodeException("无法序列化 OpenCode 请求", ex);
        }
    }

    private JsonNode delete(String path) {
        return send(request(path).DELETE().build());
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(Math.max(10, properties.opencode().timeoutSeconds())))
                .header("Accept", "application/json");
        String password = properties.opencode().password();
        if (password != null && !password.isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                    (properties.opencode().username() + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        return builder;
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = response.body() == null ? "" : response.body();
                if (detail.length() > 1000) {
                    detail = detail.substring(0, 1000);
                }
                throw new OpenCodeException("OpenCode HTTP " + response.statusCode() + ": " + detail, response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new OpenCodeException("无法连接 OpenCode: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OpenCodeException("调用 OpenCode 被中断", ex);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizeVariant(String variant) {
        return variant == null || variant.isBlank() ? null : variant;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
