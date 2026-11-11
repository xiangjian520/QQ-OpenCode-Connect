package com.xiangjian.qqbot.web;

import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.domain.MessageRow;
import com.xiangjian.qqbot.onebot.OneBotConnectionRegistry;
import com.xiangjian.qqbot.opencode.OpenCodeClient;
import com.xiangjian.qqbot.opencode.OpenCodeModels;
import com.xiangjian.qqbot.repository.AuditRepository;
import com.xiangjian.qqbot.repository.ConversationRepository;
import com.xiangjian.qqbot.repository.JobRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.service.ChatOrchestrator;
import com.xiangjian.qqbot.service.EntertainmentService;
import com.xiangjian.qqbot.service.OpenCodeCustomModelService;
import com.xiangjian.qqbot.service.SettingsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui")
public class ApiController {
    private final ChatOrchestrator orchestrator;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final JobRepository jobs;
    private final SettingsService settings;
    private final EntertainmentService entertainment;
    private final OpenCodeCustomModelService customModels;
    private final OpenCodeClient openCode;
    private final OneBotConnectionRegistry oneBot;
    private final UiEventHub events;
    private final AuditRepository audit;

    public ApiController(ChatOrchestrator orchestrator,
                         ConversationRepository conversations,
                         MessageRepository messages,
                         JobRepository jobs,
                         SettingsService settings,
                         EntertainmentService entertainment,
                         OpenCodeCustomModelService customModels,
                         OpenCodeClient openCode,
                         OneBotConnectionRegistry oneBot,
                         UiEventHub events,
                         AuditRepository audit) {
        this.orchestrator = orchestrator;
        this.conversations = conversations;
        this.messages = messages;
        this.jobs = jobs;
        this.settings = settings;
        this.entertainment = entertainment;
        this.customModels = customModels;
        this.openCode = openCode;
        this.oneBot = oneBot;
        this.events = events;
        this.audit = audit;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", Instant.now().toString());
        result.put("onebot", oneBot.status());
        OpenCodeModels.Info info = openCode.info();
        result.put("opencode", Map.of(
                "healthy", info.healthy(),
                "version", info.version(),
                "baseUrl", openCode.baseUrl(),
                "error", info.error()
        ));
        try {
            result.put("defaultModel", openCode.defaultModel().map(this::modelView).orElse(null));
        } catch (RuntimeException ex) {
            result.put("defaultModel", null);
            result.put("defaultModelError", ex.getMessage());
        }
        result.put("queue", Map.of(
                "pending", jobs.countByStatus("PENDING"),
                "running", jobs.countByStatus("RUNNING"),
                "failed", jobs.countByStatus("FAILED")
        ));
        result.put("settings", settings.effective());
        result.put("busyConversations", jobs.countByStatus("RUNNING"));
        return result;
    }

    @GetMapping("/conversations")
    public List<Map<String, Object>> conversations(@RequestParam(defaultValue = "60") int limit) {
        return conversations.list(limit).stream().map(this::conversationView).toList();
    }

    @GetMapping("/conversations/{key}/messages")
    public List<MessageRow> messages(@PathVariable String key, @RequestParam(defaultValue = "200") int limit) {
        return messages.list(key, limit);
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest request) {
        orchestrator.submitWebMessage(request.text());
        return Map.of("accepted", true, "time", Instant.now().toString());
    }

    @PostMapping("/conversations/{key}/reset")
    public Map<String, Object> reset(@PathVariable String key) {
        orchestrator.resetConversation(key);
        audit.record("web-admin", "reset", key, null);
        return Map.of("ok", true);
    }

    @PostMapping("/conversations/{key}/stop")
    public Map<String, Object> stop(@PathVariable String key) {
        int cancelled = orchestrator.stopConversation(key);
        audit.record("web-admin", "stop", key, "cancelled=" + cancelled);
        return Map.of("ok", true, "cancelled", cancelled);
    }

    @GetMapping("/models")
    public List<Map<String, Object>> models() {
        return customModels.listAll().stream().map(this::modelView).toList();
    }

    @GetMapping("/onebot/settings")
    public Map<String, Object> oneBotSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(settings.oneBotSettings());
        result.put("status", oneBot.status());
        return result;
    }

    @PutMapping("/onebot/settings")
    public Map<String, Object> updateOneBotSettings(@RequestBody OneBotUpdate request) {
        Map<String, String> values = new LinkedHashMap<>();
        if (request.token() != null && !request.token().isBlank()) values.put(SettingsService.ONEBOT_TOKEN, request.token());
        if (request.requireMention() != null) values.put(SettingsService.ONEBOT_REQUIRE_MENTION, request.requireMention().toString());
        if (request.allowedPrivateIds() != null) values.put(SettingsService.ONEBOT_ALLOWED_PRIVATE_IDS, request.allowedPrivateIds());
        if (request.allowedGroupIds() != null) values.put(SettingsService.ONEBOT_ALLOWED_GROUP_IDS, request.allowedGroupIds());
        if (request.adminQqIds() != null) values.put(SettingsService.ONEBOT_ADMIN_QQ_IDS, request.adminQqIds());
        settings.updateOneBot(values);
        audit.record("web-admin", "onebot-settings-update", null, "updated");
        events.publish("onebot-settings-changed", settings.oneBotSettings());
        return oneBotSettings();
    }

    @PostMapping("/onebot/token/regenerate")
    public Map<String, Object> regenerateOneBotToken() {
        String token = settings.regenerateOneBotToken();
        audit.record("web-admin", "onebot-token-regenerate", null, null);
        events.log("INFO", "OneBot Token 已重新生成");
        return Map.of("token", token, "settings", oneBotSettings());
    }

    @GetMapping("/opencode/settings")
    public Map<String, Object> openCodeSettings() {
        Map<String, Object> result = new LinkedHashMap<>(settings.openCodeSettings());
        result.put("customModels", customModels.customModels().stream().map(this::modelView).toList());
        OpenCodeModels.Info info = openCode.info();
        result.put("connection", Map.of(
                "healthy", info.healthy(),
                "version", info.version(),
                "baseUrl", openCode.baseUrl(),
                "error", info.error()
        ));
        try {
            result.put("activeDefaultModel", openCode.defaultModel().map(this::modelView).orElse(null));
        } catch (RuntimeException ex) {
            result.put("activeDefaultModel", null);
            result.put("error", ex.getMessage());
        }
        return result;
    }

    @PutMapping("/opencode/settings")
    public Map<String, Object> updateOpenCodeSettings(@RequestBody OpenCodeDefaults request) {
        settings.updateOpenCodeDefaults(request.providerId(), request.modelId(), request.variant());
        audit.record("web-admin", "opencode-settings-update", null,
                request.providerId() + "/" + request.modelId() + "/" + request.variant());
        events.publish("opencode-settings-changed", settings.openCodeSettings());
        return openCodeSettings();
    }

    @PostMapping("/opencode/custom-models")
    public Map<String, Object> saveCustomModel(@Valid @RequestBody CustomModelRequest request) {
        OpenCodeModels.Model saved = customModels.save(
                request.providerId(), request.modelId(), request.displayName(), request.variants());
        audit.record("web-admin", "opencode-custom-model-save", saved.label(), saved.variants().toString());
        events.log("INFO", "已保存自定义模型: " + saved.label());
        events.publish("opencode-settings-changed", Map.of("providerId", saved.providerId(), "modelId", saved.id()));
        Map<String, Object> result = openCodeSettings();
        result.put("savedModel", modelView(saved));
        return result;
    }

    @PostMapping("/opencode/custom-models/delete")
    public Map<String, Object> deleteCustomModel(@Valid @RequestBody CustomModelDelete request) {
        boolean removed = customModels.delete(request.providerId(), request.modelId());
        if (removed && request.providerId().equals(settings.defaultProviderId())
                && request.modelId().equals(settings.defaultModelId())) {
            settings.updateOpenCodeDefaults("", "", settings.defaultValueVariant());
        }
        audit.record("web-admin", "opencode-custom-model-delete", request.providerId() + "/" + request.modelId(),
                removed ? "deleted" : "not-found");
        events.log("INFO", "已删除自定义模型: " + request.providerId() + "/" + request.modelId());
        events.publish("opencode-settings-changed", Map.of("deleted", removed));
        Map<String, Object> result = openCodeSettings();
        result.put("deleted", removed);
        return result;
    }

    @GetMapping("/entertainment")
    public Map<String, Object> entertainment() {
        return entertainment.overview();
    }

    @PostMapping("/entertainment/run")
    public Map<String, Object> runEntertainment(@Valid @RequestBody EntertainmentRun request) {
        orchestrator.submitWebMessage(request.command());
        return Map.of("accepted", true);
    }

    @PostMapping("/conversations/{key}/model")
    public Map<String, Object> updateModel(@PathVariable String key, @RequestBody ModelUpdate request) {
        Conversation conversation = requireConversation(key);
        OpenCodeModels.Model model = customModels.find(request.providerId(), request.modelId())
                .orElseThrow(() -> new IllegalArgumentException("模型不存在或当前不可用"));
        String variant = model.supports(request.variant()) ? request.variant() : null;
        conversations.updateModel(key, model.providerId(), model.id(), variant);
        if (conversation.opencodeSessionId() != null) {
            openCode.switchModel(conversation.opencodeSessionId(),
                    new OpenCodeModels.ModelRef(model.id(), model.providerId(), variant));
        }
        audit.record("web-admin", "switch-model", key, model.label() + "/" + variant);
        events.publish("status-changed", Map.of("reason", "model", "conversationKey", key));
        return conversationView(conversations.find(key).orElseThrow());
    }

    @PostMapping("/conversations/{key}/variant")
    public Map<String, Object> updateVariant(@PathVariable String key, @RequestBody VariantUpdate request) {
        Conversation conversation = requireConversation(key);
        List<OpenCodeModels.Model> models = customModels.listAll();
        OpenCodeModels.Model model;
        if (conversation.modelProvider() == null || conversation.modelId() == null) {
            model = openCode.defaultModel()
                    .orElseThrow(() -> new IllegalStateException("OpenCode 尚未配置可用模型"));
        } else {
            model = models.stream()
                    .filter(candidate -> candidate.providerId().equals(conversation.modelProvider())
                            && candidate.id().equals(conversation.modelId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("当前模型已不可用"));
        }
        if (!model.supports(request.variant())) {
            throw new IllegalArgumentException("当前模型不支持思考强度: " + request.variant());
        }
        conversations.updateModel(key, model.providerId(), model.id(), request.variant());
        if (conversation.opencodeSessionId() != null) {
            openCode.switchModel(conversation.opencodeSessionId(),
                    new OpenCodeModels.ModelRef(model.id(), model.providerId(), request.variant()));
        }
        audit.record("web-admin", "switch-variant", key, request.variant());
        events.publish("status-changed", Map.of("reason", "variant", "conversationKey", key));
        return conversationView(conversations.find(key).orElseThrow());
    }

    @GetMapping("/settings")
    public Map<String, String> settings() {
        return settings.effective();
    }

    @PutMapping("/settings")
    public Map<String, String> updateSettings(@RequestBody Map<String, String> values) {
        Map<String, String> result = settings.update(values);
        audit.record("web-admin", "settings-update", null, values.toString());
        events.publish("settings-changed", result);
        events.log("INFO", "Web 管理员更新了运行设置");
        return result;
    }

    @GetMapping("/logs")
    public List<UiEventHub.LogEntry> logs(@RequestParam(defaultValue = "120") int limit) {
        return events.logs(limit);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "100") int limit) {
        return audit.recent(limit);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return events.subscribe();
    }

    private Conversation requireConversation(String key) {
        return conversations.find(key).orElseGet(() -> {
            if ("web:WEB:admin".equals(key)) {
                return conversations.createWebConversation("网页管理员");
            }
            throw new IllegalArgumentException("会话不存在");
        });
    }

    private Map<String, Object> conversationView(Conversation conversation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("key", conversation.conversationKey());
        value.put("platform", conversation.platform());
        value.put("chatType", conversation.chatType().name());
        value.put("chatId", conversation.chatId());
        value.put("displayName", conversation.displayName());
        value.put("opencodeSessionId", conversation.opencodeSessionId());
        value.put("modelProvider", conversation.modelProvider());
        value.put("modelId", conversation.modelId());
        value.put("modelVariant", conversation.modelVariant());
        value.put("agentProfile", conversation.agentProfile());
        value.put("codeModeUntil", conversation.codeModeUntil());
        value.put("updatedAt", conversation.updatedAt());
        value.put("activeJobs", jobs.activeCount(conversation.conversationKey()));
        return value;
    }

    private Map<String, Object> modelView(OpenCodeModels.Model model) {
        return customModels.view(model);
    }

    public record ChatRequest(@NotBlank String text) {}

    public record ModelUpdate(String providerId, String modelId, String variant) {}

    public record VariantUpdate(String variant) {}

    public record OneBotUpdate(
            String token,
            Boolean requireMention,
            String allowedPrivateIds,
            String allowedGroupIds,
            String adminQqIds
    ) {}

    public record OpenCodeDefaults(String providerId, String modelId, String variant) {}

    public record CustomModelRequest(
            @NotBlank String providerId,
            @NotBlank String modelId,
            String displayName,
            List<String> variants
    ) {}

    public record CustomModelDelete(@NotBlank String providerId, @NotBlank String modelId) {}

    public record EntertainmentRun(@NotBlank String command) {}
}
