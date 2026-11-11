package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.domain.InboundMessage;
import com.xiangjian.qqbot.domain.OutboundMessage;
import com.xiangjian.qqbot.onebot.OneBotConnectionRegistry;
import com.xiangjian.qqbot.onebot.OneBotGateway;
import com.xiangjian.qqbot.opencode.OpenCodeClient;
import com.xiangjian.qqbot.opencode.OpenCodeModels;
import com.xiangjian.qqbot.repository.AuditRepository;
import com.xiangjian.qqbot.repository.ConversationRepository;
import com.xiangjian.qqbot.repository.JobRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.web.UiEventHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CommandService {
    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final long CODE_MODE_DURATION_MS = Duration.ofMinutes(30).toMillis();

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final JobRepository jobs;
    private final SettingsService settings;
    private final PermissionService permissions;
    private final OpenCodeCustomModelService customModels;
    private final OpenCodeClient openCode;
    private final OneBotGateway oneBot;
    private final OneBotConnectionRegistry oneBotRegistry;
    private final MessageSplitter splitter;
    private final AuditRepository audit;
    private final UiEventHub events;

    public CommandService(ConversationRepository conversations,
                          MessageRepository messages,
                          JobRepository jobs,
                          SettingsService settings,
                          PermissionService permissions,
                          OpenCodeCustomModelService customModels,
                          OpenCodeClient openCode,
                          OneBotGateway oneBot,
                          OneBotConnectionRegistry oneBotRegistry,
                          MessageSplitter splitter,
                          AuditRepository audit,
                          UiEventHub events) {
        this.conversations = conversations;
        this.messages = messages;
        this.jobs = jobs;
        this.settings = settings;
        this.permissions = permissions;
        this.customModels = customModels;
        this.openCode = openCode;
        this.oneBot = oneBot;
        this.oneBotRegistry = oneBotRegistry;
        this.splitter = splitter;
        this.audit = audit;
        this.events = events;
    }

    public boolean tryHandle(InboundMessage message, Conversation conversation) {
        Optional<CommandParser.Command> parsed = CommandParser.parse(message.text());
        if (parsed.isEmpty()) {
            return false;
        }
        CommandParser.Command command = parsed.get();
        audit.record(message.userId(), "/" + command.name(), message.conversationKey(), command.args());
        switch (command.name()) {
            case "帮助" -> help(message);
            case "状态" -> status(message, conversation);
            case "模型" -> model(message, conversation, command.args());
            case "思考" -> thinking(message, conversation, command.args());
            case "新对话" -> reset(message, conversation);
            case "停止" -> stop(message, conversation);
            case "编码" -> coding(message, conversation, command.args());
            case "关于" -> about(message);
            default -> reply(message, "未知命令。发送 /帮助 查看可用命令。");
        }
        return true;
    }

    public void reply(InboundMessage message, String text) {
        int chunkSize = settings.replyChunkSize();
        List<String> chunks = splitter.split(text, chunkSize);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            boolean sent;
            if (message.chatType() == ChatType.WEB) {
                sent = true;
            } else {
                OutboundMessage outbound = new OutboundMessage(message.chatType(), message.chatId(), chunk,
                        i == 0 ? message.messageId() : null);
                sent = oneBot.send(outbound);
            }
            messages.insertOutbound(message.conversationKey(), null, "bot", chunk, sent ? "sent" : "send-failed");
            events.publish("message", Map.of(
                    "conversationKey", message.conversationKey(),
                    "direction", "OUT",
                    "content", chunk,
                    "status", sent ? "sent" : "send-failed",
                    "time", Instant.now().toString()
            ));
            if (settings.replyDelayMs() > 0 && i + 1 < chunks.size()) {
                try {
                    Thread.sleep(settings.replyDelayMs());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void replyImage(InboundMessage message, String resourcePath, String altText) {
        replyImageSource(message, resourcePath, altText, null);
    }

    public void replyImageUrl(InboundMessage message, String imageUrl, String altText) {
        replyImageSource(message, null, altText, imageUrl);
    }

    private void replyImageSource(InboundMessage message, String resourcePath, String altText, String imageUrl) {
        try {
            boolean sent = true;
            if (message.chatType() != ChatType.WEB) {
                if (imageUrl != null) {
                    sent = oneBot.sendImageUrl(message.chatType(), message.chatId(), imageUrl, message.messageId());
                } else {
                    byte[] image = new ClassPathResource("static" + resourcePath).getContentAsByteArray();
                    sent = oneBot.sendImage(message.chatType(), message.chatId(), image, message.messageId());
                }
            }
            String status = sent ? "sent" : "send-failed";
            String content = "[[img:" + (imageUrl == null ? resourcePath : imageUrl) + "|" + altText + "]]";
            messages.insertOutbound(message.conversationKey(), null, "bot", content, status);
            events.publish("message", Map.of(
                    "conversationKey", message.conversationKey(),
                    "direction", "OUT",
                    "content", content,
                    "status", status,
                    "time", Instant.now().toString()
            ));
        } catch (Exception ex) {
            log.warn("Could not send entertainment image {}", imageUrl == null ? resourcePath : imageUrl, ex);
            reply(message, altText);
        }
    }

    private void help(InboundMessage message) {
        reply(message, """
                QQ-OpenCode-Connect 命令
                /帮助 - 显示本帮助
                /状态 - 查看连接、模型和队列状态
                /模型 - 查看模型；管理员可用 /模型 序号 切换
                /思考 - 查看思考强度；管理员可设 关闭、低、中、高
                /新对话 - 清空当前上下文
                /停止 - 中断回复并清空队列
                /编码 开启|关闭 - 仅管理员私聊，30 分钟有效
                /娱乐 - 查看安全娱乐功能和游戏命令
                /关于 - 项目信息
                群聊中所有命令都需要先 @Bot。""");
    }

    private void status(InboundMessage message, Conversation conversation) {
        OpenCodeModels.Info info = openCode.info();
        String model = conversation.modelProvider() == null
                ? "默认模型"
                : conversation.modelProvider() + "/" + conversation.modelId();
        String variant = conversation.modelVariant() == null ? settings.defaultValueVariant() : conversation.modelVariant();
        long codeRemaining = conversation.codeModeUntil() == null ? 0
                : Math.max(0, conversation.codeModeUntil() - System.currentTimeMillis());
        reply(message, """
                QQ 连接: %s
                OpenCode: %s (%s)
                当前模型: %s
                思考强度: %s
                当前会话: %s
                队列中: %d
                编码模式: %s""".formatted(
                oneBotRegistry.connected() ? "已连接" : "未连接",
                info.healthy() ? "可用" : "不可用",
                info.version(),
                model,
                chineseVariant(variant),
                conversation.opencodeSessionId() == null ? "尚未创建" : conversation.opencodeSessionId(),
                jobs.activeCount(message.conversationKey()),
                codeRemaining > 0 ? "开启，剩余 " + (codeRemaining / 60000 + 1) + " 分钟" : "关闭"
        ));
    }

    private void model(InboundMessage message, Conversation conversation, String args) {
        List<OpenCodeModels.Model> models;
        try {
            models = customModels.listAll();
        } catch (RuntimeException ex) {
            reply(message, "读取模型失败：" + ex.getMessage());
            return;
        }
        if (args.isBlank()) {
            StringBuilder text = new StringBuilder("当前模型: ");
            text.append(conversation.modelProvider() == null ? "OpenCode 默认"
                    : conversation.modelProvider() + "/" + conversation.modelId());
            text.append("\n可用模型:\n");
            int limit = Math.min(models.size(), 12);
            for (int i = 0; i < limit; i++) {
                OpenCodeModels.Model model = models.get(i);
                text.append(i + 1).append(". ").append(model.label());
                if (!model.name().isBlank() && !model.name().equals(model.modelId())) {
                    text.append(" - ").append(model.name());
                }
                text.append('\n');
            }
            if (models.size() > limit) {
                text.append("仅显示前 ").append(limit).append(" 个模型\n");
            }
            text.append("管理员使用 /模型 序号 切换。");
            reply(message, text.toString());
            return;
        }
        if (!permissions.isAdmin(message.userId())) {
            reply(message, "只有管理员可以切换模型。");
            return;
        }
        OpenCodeModels.Model selected = selectModel(models, args);
        if (selected == null) {
            reply(message, "未找到该模型，请先发送 /模型 查看序号。");
            return;
        }
        String variant = conversation.modelVariant();
        if (variant == null || !selected.supports(variant)) {
            variant = selected.supports(settings.defaultValueVariant()) ? settings.defaultValueVariant() : null;
        }
        conversations.updateModel(conversation.conversationKey(), selected.providerId(), selected.id(), variant);
        if (conversation.opencodeSessionId() != null) {
            try {
                openCode.switchModel(conversation.opencodeSessionId(),
                        new OpenCodeModels.ModelRef(selected.id(), selected.providerId(), variant));
            } catch (RuntimeException ex) {
                reply(message, "模型已保存，但切换当前 OpenCode 会话失败：" + ex.getMessage());
                return;
            }
        }
        reply(message, "已切换模型为 " + selected.label() + "。");
        events.publish("status-changed", Map.of("reason", "model", "model", selected.label()));
    }

    private void thinking(InboundMessage message, Conversation conversation, String args) {
        if (args.isBlank()) {
            String variant = conversation.modelVariant() == null ? settings.defaultValueVariant() : conversation.modelVariant();
            reply(message, "当前思考强度：" + chineseVariant(variant) + " (" + variant + ")\n管理员可设：/思考 关闭|低|中|高");
            return;
        }
        if (!permissions.isAdmin(message.userId())) {
            reply(message, "只有管理员可以修改思考强度。");
            return;
        }
        String variant = switch (args.strip()) {
            case "关闭", "无", "none" -> "none";
            case "低", "low" -> "low";
            case "中", "medium" -> "medium";
            case "高", "high" -> "high";
            default -> null;
        };
        if (variant == null) {
            reply(message, "思考强度只支持：关闭、低、中、高。");
            return;
        }
        if (conversation.modelProvider() != null && conversation.modelId() != null) {
            OpenCodeModels.Model currentModel = customModels.listAll().stream()
                    .filter(model -> model.providerId().equals(conversation.modelProvider())
                            && model.id().equals(conversation.modelId()))
                    .findFirst().orElse(null);
            if (currentModel != null && !currentModel.supports(variant)) {
                reply(message, "当前模型不支持思考强度「" + chineseVariant(variant) + "」，可用值："
                        + String.join("、", currentModel.variants()));
                return;
            }
        }
        conversations.updateModel(conversation.conversationKey(), conversation.modelProvider(), conversation.modelId(), variant);
        if (conversation.opencodeSessionId() != null && conversation.modelProvider() != null) {
            try {
                openCode.switchModel(conversation.opencodeSessionId(),
                        new OpenCodeModels.ModelRef(conversation.modelId(), conversation.modelProvider(), variant));
            } catch (RuntimeException ex) {
                reply(message, "设置已保存，但当前会话应用失败：" + ex.getMessage());
                return;
            }
        }
        reply(message, "思考强度已设为" + chineseVariant(variant) + "。");
    }

    private void reset(InboundMessage message, Conversation conversation) {
        if (!permissions.canResetOrStop(message.chatType(), message.userId())) {
            reply(message, "群聊中只有管理员可以新建对话。");
            return;
        }
        conversations.clearSession(conversation.conversationKey());
        jobs.cancelConversation(conversation.conversationKey());
        if (conversation.opencodeSessionId() != null) {
            openCode.interrupt(conversation.opencodeSessionId());
        }
        reply(message, "已创建新对话，下一条消息将使用新的上下文。");
        events.publish("conversation-changed", Map.of("conversationKey", conversation.conversationKey()));
    }

    private void stop(InboundMessage message, Conversation conversation) {
        if (!permissions.canResetOrStop(message.chatType(), message.userId())) {
            reply(message, "群聊中只有管理员可以停止回复。");
            return;
        }
        int cancelled = jobs.cancelConversation(conversation.conversationKey());
        if (conversation.opencodeSessionId() != null) {
            try {
                openCode.interrupt(conversation.opencodeSessionId());
            } catch (RuntimeException ex) {
                log.debug("Interrupt failed: {}", ex.getMessage());
            }
        }
        reply(message, "已停止当前回复并取消 " + cancelled + " 个排队任务。");
    }

    private void coding(InboundMessage message, Conversation conversation, String args) {
        if (!permissions.canEnableCoding(message.chatType(), message.userId())) {
            reply(message, "编码模式仅允许管理员私聊使用。");
            return;
        }
        String action = args.isBlank() ? "" : args.strip();
        if (action.equals("开启")) {
            Long until = System.currentTimeMillis() + CODE_MODE_DURATION_MS;
            conversations.updateAgent(conversation.conversationKey(), "qq-code", until);
            if (conversation.opencodeSessionId() != null) {
                openCode.switchAgent(conversation.opencodeSessionId(), "qq-code");
            }
            reply(message, "编码模式已开启，30 分钟后自动关闭。工作区已隔离，请勿让 OpenCode 操作未授权目录。");
        } else if (action.equals("关闭")) {
            conversations.updateAgent(conversation.conversationKey(), "qq-chat", null);
            if (conversation.opencodeSessionId() != null) {
                openCode.switchAgent(conversation.opencodeSessionId(), "qq-chat");
            }
            reply(message, "编码模式已关闭。");
        } else {
            long remaining = conversation.codeModeUntil() == null ? 0
                    : Math.max(0, conversation.codeModeUntil() - System.currentTimeMillis());
            reply(message, "编码模式当前" + (remaining > 0 ? "已开启，剩余 " + (remaining / 60000 + 1) + " 分钟" : "关闭")
                    + "。使用 /编码 开启 或 /编码 关闭。");
        }
    }

    private void about(InboundMessage message) {
        reply(message, """
                QQ-OpenCode-Connect v0.1.0
                后端: Java 25 / Spring Boot 4
                QQ: OneBot v11 / NapCat
                AI: OpenCode v2
                项目: https://github.com/xiangjian520/QQ-OpenCode-Connect""");
    }

    private OpenCodeModels.Model selectModel(List<OpenCodeModels.Model> models, String selection) {
        try {
            int index = Integer.parseInt(selection) - 1;
            return index >= 0 && index < models.size() ? models.get(index) : null;
        } catch (NumberFormatException ignored) {
        }
        String wanted = selection.toLowerCase(Locale.ROOT);
        return models.stream()
                .filter(model -> model.label().equalsIgnoreCase(wanted)
                        || model.name().equalsIgnoreCase(wanted)
                        || model.modelId().equalsIgnoreCase(wanted))
                .findFirst().orElse(null);
    }

    private static String chineseVariant(String variant) {
        if (variant == null) {
            return "默认";
        }
        return switch (variant) {
            case "none" -> "关闭";
            case "minimal" -> "最低";
            case "low" -> "低";
            case "medium" -> "中";
            case "high" -> "高";
            case "xhigh", "max" -> "最高";
            default -> variant;
        };
    }
}
