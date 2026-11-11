package com.xiangjian.qqbot.onebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.QqPlatformAdapter;
import com.xiangjian.qqbot.domain.OutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OneBotGateway implements QqPlatformAdapter {
    private static final Logger log = LoggerFactory.getLogger(OneBotGateway.class);

    private final OneBotConnectionRegistry registry;
    private final ObjectMapper objectMapper;
    private final AtomicLong echo = new AtomicLong();

    public OneBotGateway(OneBotConnectionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "onebot-v11";
    }

    @Override
    public boolean connected() {
        return registry.connected();
    }

    @Override
    public Map<String, Object> status() {
        return registry.status();
    }

    @Override
    public boolean send(OutboundMessage outbound) {
        if (!registry.connected()) {
            log.warn("Cannot send QQ message: OneBot is not connected");
            return false;
        }
        String action = outbound.chatType() == ChatType.GROUP ? "send_group_msg" : "send_private_msg";
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", action);
        payload.put("echo", "qqbot-" + echo.incrementAndGet());
        ObjectNode params = payload.putObject("params");
        if (outbound.chatType() == ChatType.GROUP) {
            params.put("group_id", outbound.chatId());
        } else {
            params.put("user_id", outbound.chatId());
        }
        ArrayNode message = params.putArray("message");
        if (outbound.replyToMessageId() != null && !outbound.replyToMessageId().isBlank()) {
            ObjectNode reply = message.addObject();
            reply.put("type", "reply");
            reply.putObject("data").put("id", outbound.replyToMessageId());
        }
        ObjectNode text = message.addObject();
        text.put("type", "text");
        text.putObject("data").put("text", outbound.text());
        return broadcast(payload);
    }

    public boolean sendRawAction(String action, Map<String, ?> params) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", action);
        payload.put("echo", "qqbot-" + echo.incrementAndGet());
        payload.set("params", objectMapper.valueToTree(params));
        return broadcast(payload);
    }

    private boolean broadcast(ObjectNode payload) {
        List<WebSocketSession> sessions = registry.sessions().stream()
                .filter(WebSocketSession::isOpen)
                .toList();
        if (sessions.isEmpty()) {
            return false;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.error("Could not serialize OneBot action", ex);
            return false;
        }
        boolean sent = false;
        for (WebSocketSession session : sessions) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
                sent = true;
            } catch (Exception ex) {
                log.warn("Could not send OneBot action on session {}: {}", session.getId(), ex.getMessage());
            }
        }
        return sent;
    }
}
