package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.config.AppProperties;
import com.xiangjian.qqbot.domain.ChatJob;
import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.domain.InboundMessage;
import com.xiangjian.qqbot.domain.OutboundMessage;
import com.xiangjian.qqbot.onebot.InboundMessageHandler;
import com.xiangjian.qqbot.onebot.OneBotGateway;
import com.xiangjian.qqbot.opencode.OpenCodeClient;
import com.xiangjian.qqbot.opencode.OpenCodeEventService;
import com.xiangjian.qqbot.opencode.OpenCodeException;
import com.xiangjian.qqbot.opencode.OpenCodeModels;
import com.xiangjian.qqbot.repository.ConversationRepository;
import com.xiangjian.qqbot.repository.JobRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.web.UiEventHub;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ChatOrchestrator implements InboundMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final JobRepository jobs;
    private final SettingsService settings;
    private final PermissionService permissions;
    private final OpenCodeCustomModelService customModels;
    private final EntertainmentService entertainment;
    private final CommandService commands;
    private final OpenCodeClient openCode;
    private final OpenCodeEventService openCodeEvents;
    private final OneBotGateway oneBot;
    private final MessageSplitter splitter;
    private final UiEventHub events;
    private final AppProperties properties;
    private final Set<String> busyConversations = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatOrchestrator(ConversationRepository conversations,
                            MessageRepository messages,
                            JobRepository jobs,
                            SettingsService settings,
                            PermissionService permissions,
                            OpenCodeCustomModelService customModels,
                            EntertainmentService entertainment,
                            CommandService commands,
                            OpenCodeClient openCode,
                            OpenCodeEventService openCodeEvents,
                            OneBotGateway oneBot,
                            MessageSplitter splitter,
                            UiEventHub events,
                            AppProperties properties) {
        this.conversations = conversations;
        this.messages = messages;
        this.jobs = jobs;
        this.settings = settings;
        this.permissions = permissions;
        this.customModels = customModels;
        this.entertainment = entertainment;
        this.commands = commands;
        this.openCode = openCode;
        this.openCodeEvents = openCodeEvents;
        this.oneBot = oneBot;
        this.splitter = splitter;
        this.events = events;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverJobs() {
        jobs.recoverRunning();
        events.log("INFO", "任务队列已恢复，" + jobs.countByStatus("PENDING") + " 个待处理任务");
    }

    @Override
    public void handle(InboundMessage message) {
        if (message.text().isBlank()) {
            return;
        }
        if (!permissions.canUse(message)) {
            log.debug("Ignored message from unauthorized {}", message.userId());
            return;
        }
        if (message.isGroup() && settings.requireMention() && !message.mentionedBot()) {
            return;
        }

        conversations.createIfMissing(message);
        if (!messages.insertInbound(message.conversationKey(), message.messageId(), message.userId(), message.text())) {
            return;
        }
        Conversation conversation = conversations.find(message.conversationKey()).orElseThrow();
        events.publish("message", Map.of(
                "conversationKey", conversation.conversationKey(),
                "direction", "IN",
                "content", message.text(),
                "sender", message.displayName(),
                "time", Instant.now().toString()
        ));

        if (entertainment.tryHandle(message, conversation) || commands.tryHandle(message, conversation)) {
            messages.markInboundStatus(message.messageId(), "command");
            return;
        }

        if (jobs.activeCount(message.conversationKey()) >= settings.queueMax()) {
            messages.markInboundStatus(message.messageId(), "queue-full");
            commands.reply(message, "当前会话排队已满，请稍后再试或发送 /停止。");
            return;
        }

        if (jobs.enqueue(message.conversationKey(), message.messageId(), message.text())) {
            events.publish("queue-changed", Map.of("conversationKey", message.conversationKey(), "pending", jobs.activeCount(message.conversationKey())));
            events.log("INFO", "已排队 QQ 消息: " + message.conversationKey());
        }
    }

    public void submitWebMessage(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        Conversation conversation = conversations.createWebConversation("网页管理员");
        String messageId = "web-" + UUID.randomUUID();
        String normalized = text.strip();
        if (!messages.insertInbound(conversation.conversationKey(), messageId, "web-admin", normalized)) {
            throw new IllegalStateException("消息重复");
        }
        InboundMessage webMessage = new InboundMessage(
                "web", ChatType.WEB, "admin", "web-admin", "网页管理员",
                normalized, false, messageId, System.currentTimeMillis()
        );
        events.publish("message", Map.of(
                "conversationKey", conversation.conversationKey(),
                "direction", "IN",
                "content", normalized,
                "sender", "网页管理员",
                "time", Instant.now().toString()
        ));
        if (entertainment.tryHandle(webMessage, conversation) || commands.tryHandle(webMessage, conversation)) {
            messages.markInboundStatus(messageId, "command");
            return;
        }
        if (jobs.activeCount(conversation.conversationKey()) >= settings.queueMax()) {
            messages.markInboundStatus(messageId, "queue-full");
            throw new IllegalStateException("当前网页会话队列已满");
        }
        jobs.enqueue(conversation.conversationKey(), messageId, normalized);
    }

    public int stopConversation(String conversationKey) {
        Conversation conversation = conversations.find(conversationKey)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        int cancelled = jobs.cancelConversation(conversationKey);
        if (conversation.opencodeSessionId() != null) {
            try {
                openCode.interrupt(conversation.opencodeSessionId());
            } catch (RuntimeException ex) {
                log.debug("OpenCode interrupt failed: {}", ex.getMessage());
            }
        }
        events.publish("queue-changed", Map.of("conversationKey", conversationKey, "cancelled", cancelled));
        return cancelled;
    }

    public void resetConversation(String conversationKey) {
        Conversation conversation = conversations.find(conversationKey)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        stopConversation(conversationKey);
        conversations.clearSession(conversationKey);
        events.publish("conversation-changed", Map.of("conversationKey", conversationKey));
    }

    @Scheduled(fixedDelayString = "${qqbot.queue.poll-ms:500}")
    public void pollQueue() {
        ChatJob job = jobs.claimNext(busyConversations);
        if (job == null) {
            return;
        }
        busyConversations.add(job.conversationKey());
        executor.submit(() -> process(job));
    }

    private void process(ChatJob job) {
        Conversation conversation = null;
        try {
            conversation = conversations.find(job.conversationKey())
                    .orElseThrow(() -> new OpenCodeException("会话不存在", -1));
            if (conversation.codeModeUntil() != null && conversation.codeModeUntil() <= System.currentTimeMillis()) {
                conversations.updateAgent(conversation.conversationKey(), "qq-chat", null);
                conversation = conversations.find(conversation.conversationKey()).orElseThrow();
            }

            String sessionId = ensureSession(conversation);
            CompletableFuture<Void> waiter = openCodeEvents.prepareIdleWaiter(sessionId);
            long promptStartedAt = System.currentTimeMillis();
            openCode.prompt(sessionId, job.text(), conversation.conversationKey());
            events.publish("job-changed", Map.of("jobId", job.id(), "status", "RUNNING"));
            awaitOpenCodeCompletion(sessionId, promptStartedAt, waiter, job.id());

            if ("CANCELLED".equals(jobs.status(job.id()))) {
                events.publish("job-changed", Map.of("jobId", job.id(), "status", "CANCELLED"));
                return;
            }

            OpenCodeModels.AssistantReply reply = openCode.latestAssistantReply(sessionId, promptStartedAt)
                    .orElseThrow(() -> new OpenCodeException("OpenCode 已完成但没有返回文本内容", -1));
            deliver(conversations.find(job.conversationKey()).orElse(conversation), job, reply);
            jobs.complete(job.id());
            events.publish("job-changed", Map.of("jobId", job.id(), "status", "DONE"));
            events.log("INFO", "OpenCode 回复已发送: " + job.conversationKey());
        } catch (Exception ex) {
            if ("CANCELLED".equals(jobs.status(job.id()))) {
                events.publish("job-changed", Map.of("jobId", job.id(), "status", "CANCELLED"));
                return;
            }
            String error = friendlyError(ex);
            jobs.fail(job.id(), error);
            events.publish("job-changed", Map.of("jobId", job.id(), "status", "FAILED", "error", error));
            events.log("ERROR", "任务 " + job.id() + " 失败: " + error);
            log.warn("Job {} failed", job.id(), ex);
            if (conversation != null) {
                deliverError(conversation, job, error);
            }
        } finally {
            busyConversations.remove(job.conversationKey());
        }
    }

    private void awaitOpenCodeCompletion(String sessionId, long promptStartedAt,
                                         CompletableFuture<Void> waiter, long jobId) {
        long deadline = System.currentTimeMillis() + Math.max(30, properties.opencode().timeoutSeconds()) * 1000L;
        try {
            while (System.currentTimeMillis() < deadline) {
                if ("CANCELLED".equals(jobs.status(jobId))) {
                    return;
                }
                if (waiter.isCompletedExceptionally() || waiter.isCancelled()) {
                    openCodeEvents.awaitIdle(sessionId, waiter);
                }
                if (waiter.isDone()) {
                    return;
                }
                long idleAt = openCode.sessionIdleAt(sessionId);
                if (idleAt >= promptStartedAt - 1_000) {
                    return;
                }
                Thread.sleep(500);
            }
            throw new OpenCodeException("等待 OpenCode 回复超时", -1);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OpenCodeException("等待 OpenCode 回复被中断", ex);
        } finally {
            openCodeEvents.cancelIdleWaiter(sessionId);
        }
    }

    private String ensureSession(Conversation conversation) {
        if (conversation.opencodeSessionId() != null && !conversation.opencodeSessionId().isBlank()) {
            return conversation.opencodeSessionId();
        }
        OpenCodeModels.ModelRef model = resolveModel(conversation);
        String agent = conversation.agentProfile() == null ? "qq-chat" : conversation.agentProfile();
        String sessionId = openCode.createSession("QQ-" + conversation.conversationKey(), agent, model);
        conversations.updateSession(conversation.conversationKey(), sessionId);
        events.publish("conversation-changed", Map.of("conversationKey", conversation.conversationKey()));
        return sessionId;
    }

    private OpenCodeModels.ModelRef resolveModel(Conversation conversation) {
        if (conversation.modelProvider() != null && conversation.modelId() != null) {
            return new OpenCodeModels.ModelRef(conversation.modelId(), conversation.modelProvider(), conversation.modelVariant());
        }
        String configuredProvider = settings.defaultProviderId();
        String configuredModel = settings.defaultModelId();
        OpenCodeModels.Model model = null;
        boolean customModel = false;
        if (configuredProvider != null && !configuredProvider.isBlank()
                && configuredModel != null && !configuredModel.isBlank()) {
            model = customModels.find(configuredProvider, configuredModel).orElse(null);
            customModel = model != null && customModels.isCustom(model);
            if (model == null) {
                events.log("WARN", "网页端设置的默认模型不可用: " + configuredProvider + "/" + configuredModel);
            }
        }
        if (model == null) {
            model = openCode.defaultModel()
                    .orElseThrow(() -> new OpenCodeException("OpenCode 尚未配置模型，请先执行 opencode auth login", 401));
        }
        String preferredVariant = settings.defaultValueVariant();
        String variant = conversation.modelVariant();
        if (variant == null || !model.supports(variant)) {
            if (!customModel && preferredVariant != null && !preferredVariant.isBlank()
                    && !model.supports(preferredVariant)) {
                model = customModels.listAll().stream()
                        .filter(candidate -> candidate.enabledOrAssumed() && candidate.supports(preferredVariant))
                        .findFirst()
                        .orElse(model);
            }
            variant = model.supports(preferredVariant) ? preferredVariant : null;
        }
        if (variant == null && preferredVariant != null && !preferredVariant.isBlank()) {
            events.log("WARN", "当前模型不支持思考强度 " + preferredVariant + "，已使用模型默认值");
        }
        conversations.updateModel(conversation.conversationKey(), model.providerId(), model.id(), variant);
        return new OpenCodeModels.ModelRef(model.id(), model.providerId(), variant);
    }

    private void deliver(Conversation conversation, ChatJob job, OpenCodeModels.AssistantReply reply) {
        List<String> chunks = splitter.split(reply.text(), settings.replyChunkSize());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String status;
            if (conversation.chatType() == ChatType.WEB) {
                status = "sent";
                events.publish("message", Map.of(
                        "conversationKey", conversation.conversationKey(),
                        "direction", "OUT",
                        "content", chunk,
                        "sender", "OpenCode",
                        "time", Instant.now().toString()
                ));
            } else {
                OutboundMessage outbound = new OutboundMessage(conversation.chatType(), conversation.chatId(), chunk,
                        i == 0 ? job.inboundMessageId() : null);
                status = oneBot.send(outbound) ? "sent" : "send-failed";
                events.publish("message", Map.of(
                        "conversationKey", conversation.conversationKey(),
                        "direction", "OUT",
                        "content", chunk,
                        "status", status,
                        "time", Instant.now().toString()
                ));
            }
            messages.insertOutbound(conversation.conversationKey(), reply.messageId(), "opencode", chunk, status);
            if (settings.replyDelayMs() > 0 && i + 1 < chunks.size()) {
                try {
                    Thread.sleep(settings.replyDelayMs());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void deliverError(Conversation conversation, ChatJob job, String error) {
        String text = "回复失败：" + error;
        if (conversation.chatType() == ChatType.WEB) {
            messages.insertOutbound(conversation.conversationKey(), null, "system", text, "failed");
            events.publish("message", Map.of(
                    "conversationKey", conversation.conversationKey(),
                    "direction", "OUT",
                    "content", text,
                    "status", "failed",
                    "time", Instant.now().toString()
            ));
            return;
        }
        OutboundMessage outbound = new OutboundMessage(conversation.chatType(), conversation.chatId(), text, job.inboundMessageId());
        String status = oneBot.send(outbound) ? "sent" : "send-failed";
        messages.insertOutbound(conversation.conversationKey(), null, "system", text, status);
    }

    private String friendlyError(Exception ex) {
        if (ex instanceof OpenCodeException openCodeException) {
            if (openCodeException.statusCode() == 401) {
                return "OpenCode 尚未登录或认证失败，请管理员完成 Provider 登录";
            }
            if (openCodeException.statusCode() == 409) {
                return "OpenCode 当前会话仍在处理其他任务，请稍后重试";
            }
            if (openCodeException.statusCode() == 429) {
                return "免费模型额度已用完。请管理员登录 OpenCode 账号或配置付费 Provider 后重试。";
            }
        }
        String message = ex.getMessage();
        if (message != null && (message.contains("FreeUsageLimitError") || message.contains("Free usage limit"))) {
            return "免费模型额度已用完。请管理员登录 OpenCode 账号或配置付费 Provider 后重试。";
        }
        if (message == null || message.isBlank()) {
            return "OpenCode 请求失败";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
