package com.xiangjian.qqbot.config;

import com.xiangjian.qqbot.onebot.OneBotWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Configuration
@EnableWebSocket
public class OneBotWebSocketConfig implements WebSocketConfigurer {
    private final OneBotWebSocketHandler handler;
    private final AppProperties properties;

    public OneBotWebSocketConfig(OneBotWebSocketHandler handler, AppProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/onebot/v11/ws")
                .addInterceptors(new TokenHandshakeInterceptor(properties.onebot().token()))
                .setAllowedOriginPatterns("*");
    }

    static final class TokenHandshakeInterceptor implements HandshakeInterceptor {
        private final String expectedToken;

        TokenHandshakeInterceptor(String expectedToken) {
            this.expectedToken = expectedToken == null ? "" : expectedToken;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (expectedToken.isBlank()) {
                return true;
            }
            String authorization = request.getHeaders().getFirst("Authorization");
            String supplied = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7)
                    : queryToken(request.getURI().getRawQuery());
            if (supplied != null && MessageDigest.isEqual(
                    supplied.getBytes(StandardCharsets.UTF_8),
                    expectedToken.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private static String queryToken(String rawQuery) {
            if (rawQuery == null || rawQuery.isBlank()) {
                return null;
            }
            for (String part : rawQuery.split("&")) {
                int index = part.indexOf('=');
                if (index > 0 && "access_token".equals(part.substring(0, index))) {
                    return java.net.URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}
