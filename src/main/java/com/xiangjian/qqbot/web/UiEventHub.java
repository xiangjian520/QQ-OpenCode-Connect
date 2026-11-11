package com.xiangjian.qqbot.web;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class UiEventHub {
    private static final int LOG_LIMIT = 300;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<LogEntry> logs = new ArrayDeque<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, "connected", Map.of("time", Instant.now().toString()));
        return emitter;
    }

    public void publish(String type, Object data) {
        for (SseEmitter emitter : emitters) {
            send(emitter, type, data);
        }
    }

    public void log(String level, String message) {
        LogEntry entry = new LogEntry(Instant.now().toString(), level, message);
        synchronized (logs) {
            logs.addFirst(entry);
            while (logs.size() > LOG_LIMIT) {
                logs.removeLast();
            }
        }
        publish("log", entry);
    }

    public List<LogEntry> logs(int limit) {
        synchronized (logs) {
            return new ArrayList<>(logs).stream().limit(Math.max(1, Math.min(limit, LOG_LIMIT))).toList();
        }
    }

    @Scheduled(fixedDelay = 20_000)
    void heartbeat() {
        publish("heartbeat", Map.of("time", Instant.now().toString()));
    }

    @PreDestroy
    void shutdown() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        emitters.clear();
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ex) {
            emitters.remove(emitter);
        }
    }

    public record LogEntry(String time, String level, String message) {}
}
