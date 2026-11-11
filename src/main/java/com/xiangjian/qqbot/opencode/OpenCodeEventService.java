package com.xiangjian.qqbot.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangjian.qqbot.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OpenCodeEventService {
    private static final Logger log = LoggerFactory.getLogger(OpenCodeEventService.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CompletableFuture<Void>> waiters = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile InputStream activeStream;
    private volatile Thread thread;
    private volatile long lastEventAt;
    private volatile String lastError = "";

    public OpenCodeEventService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (thread != null) {
            return;
        }
        thread = Thread.ofVirtual().name("opencode-sse").start(this::streamLoop);
    }

    public CompletableFuture<Void> prepareIdleWaiter(String sessionId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture<Void> old = waiters.put(sessionId, future);
        if (old != null) {
            old.completeExceptionally(new OpenCodeException("同一会话的旧等待已取消", -1));
        }
        return future;
    }

    public void cancelIdleWaiter(String sessionId) {
        CompletableFuture<Void> waiter = waiters.remove(sessionId);
        if (waiter != null) {
            waiter.cancel(true);
        }
    }

    public void awaitIdle(String sessionId, CompletableFuture<Void> waiter) {
        try {
            waiter.get(Math.max(30, properties.opencode().timeoutSeconds()), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            throw new OpenCodeException("等待 OpenCode 回复超时", -1);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new OpenCodeException("OpenCode 事件流报错: " + cause.getMessage(), cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OpenCodeException("等待 OpenCode 回复被中断", ex);
        } finally {
            waiters.remove(sessionId, waiter);
        }
    }

    public boolean connected() {
        return lastEventAt > 0 && System.currentTimeMillis() - lastEventAt < 45_000;
    }

    public long lastEventAt() {
        return lastEventAt;
    }

    public String lastError() {
        return lastError;
    }

    private void streamLoop() {
        String baseUrl = stripTrailingSlash(properties.opencode().baseUrl());
        while (running.get()) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/event"))
                        .timeout(Duration.ofHours(24))
                        .header("Accept", "text/event-stream")
                        .GET();
                String password = properties.opencode().password();
                if (password != null && !password.isBlank()) {
                    String token = Base64.getEncoder().encodeToString(
                            (properties.opencode().username() + ":" + password).getBytes(StandardCharsets.UTF_8));
                    builder.header("Authorization", "Basic " + token);
                }
                HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    try (InputStream ignored = response.body()) {
                        lastError = "SSE HTTP " + response.statusCode();
                    }
                    sleepReconnect();
                    continue;
                }
                lastError = "";
                try (InputStream input = response.body()) {
                    activeStream = input;
                    readEvents(input);
                } finally {
                    activeStream = null;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                log.debug("OpenCode SSE disconnected: {}", lastError);
                sleepReconnect();
            }
        }
    }

    private void readEvents(InputStream input) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        handleEvent(data.toString());
                        data.setLength(0);
                    }
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).stripLeading());
                }
            }
        }
    }

    private void handleEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            lastEventAt = System.currentTimeMillis();
            String type = root.path("type").asText();
            JsonNode eventData = root.path("data");
            String sessionId = eventData.path("sessionID").asText();
            if (sessionId.isBlank()) {
                sessionId = eventData.path("sessionId").asText();
            }
            if (sessionId.isBlank()) {
                return;
            }
            if ("session.idle".equals(type)) {
                completeWaiter(sessionId, null);
            } else if ("session.error".equals(type)) {
                completeWaiter(sessionId, new OpenCodeException("OpenCode 会话错误: " + eventData, -1));
            }
        } catch (Exception ex) {
            log.debug("Ignored malformed OpenCode SSE event: {}", ex.getMessage());
        }
    }

    private void completeWaiter(String sessionId, Throwable error) {
        CompletableFuture<Void> waiter = waiters.remove(sessionId);
        if (waiter == null) {
            return;
        }
        if (error == null) {
            waiter.complete(null);
        } else {
            waiter.completeExceptionally(error);
        }
    }

    private void sleepReconnect() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        InputStream stream = activeStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
