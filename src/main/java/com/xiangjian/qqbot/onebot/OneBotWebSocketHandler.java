package com.xiangjian.qqbot.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class OneBotWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(OneBotWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final OneBotEventParser parser;
    private final OneBotConnectionRegistry registry;
    private final InboundMessageHandler inboundHandler;

    public OneBotWebSocketHandler(ObjectMapper objectMapper,
                                  OneBotEventParser parser,
                                  OneBotConnectionRegistry registry,
                                  InboundMessageHandler inboundHandler) {
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.registry = registry;
        this.inboundHandler = inboundHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.add(session);
        log.info("OneBot connected: {} from {}", session.getId(), session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            registry.observe(root.path("self_id").asText());
            parser.parse(root).ifPresent(inboundHandler::handle);
        } catch (Exception ex) {
            log.warn("Ignored malformed OneBot message: {}", ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session.getId());
        log.info("OneBot disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("OneBot transport error on {}: {}", session.getId(), exception.getMessage());
    }
}
