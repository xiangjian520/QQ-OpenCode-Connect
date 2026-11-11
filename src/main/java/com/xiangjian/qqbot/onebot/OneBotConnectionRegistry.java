package com.xiangjian.qqbot.onebot;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class OneBotConnectionRegistry {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private volatile String selfId = "";

    public void add(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public void observe(String selfId) {
        if (selfId != null && !selfId.isBlank()) {
            this.selfId = selfId;
        }
        lastSeen.put(selfId == null ? "" : selfId, System.currentTimeMillis());
    }

    public Set<WebSocketSession> sessions() {
        return Set.copyOf(sessions.values());
    }

    public Map<String, Long> lastSeen() {
        return Map.copyOf(lastSeen);
    }

    public String selfId() {
        return selfId;
    }

    public boolean connected() {
        return sessions.values().stream().anyMatch(WebSocketSession::isOpen);
    }

    public Map<String, Object> status() {
        return Map.of(
                "connected", connected(),
                "connectionCount", sessions.size(),
                "selfId", selfId,
                "accounts", lastSeen().entrySet().stream()
                        .filter(e -> !e.getKey().isBlank())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }
}
