package com.xiangjian.qqbot.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xiangjian.qqbot.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeClientTest {
    private HttpServer server;
    private OpenCodeClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/info", exchange -> json(exchange, """
                {"version":"2.0.29"}
                """));
        server.createContext("/api/model/default", exchange -> json(exchange, """
                {"data":{"id":"openai/gpt-5","modelID":"gpt-5","providerID":"openai","name":"GPT-5",
                "variants":[{"id":"none"},{"id":"low"}],"capabilities":{},"time":{"released":1},
                "cost":[],"status":"active","enabled":true,"limit":{"context":1,"output":1}}}
                """));
        server.createContext("/api/session/ses_1", exchange -> json(exchange, """
                {"data":{"id":"ses_1","time":{"created":1000,"updated":2000,"idle":2500}}}
                """));
        server.createContext("/api/session/ses_1/message", exchange -> json(exchange, """
                {"data":[
                  {"id":"msg_2","type":"assistant","time":{"created":2000},"content":[
                    {"type":"reasoning","text":"secret"},
                    {"type":"text","text":"最终回答"}
                  ]}
                ],"cursor":{"previous":null,"next":null}}
                """));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var properties = new AppProperties(
                new AppProperties.Web("admin", "password"),
                new AppProperties.OpenCode("docker", baseUrl, "opencode", "secret", "none", 20),
                new AppProperties.OneBot("token", true, "", "", ""),
                new AppProperties.Queue(500, 5),
                new AppProperties.Reply(1800, 0),
                new AppProperties.Retention(90)
        );
        client = new OpenCodeClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsInfoAndDefaultModelWithBasicAuth() {
        assertThat(client.info()).satisfies(info -> {
            assertThat(info.healthy()).isTrue();
            assertThat(info.version()).isEqualTo("2.0.29");
        });
        var model = client.defaultModel().orElseThrow();
        assertThat(model.label()).isEqualTo("openai/gpt-5");
        assertThat(model.supports("none")).isTrue();
    }

    @Test
    void readsSessionIdleTimestamp() {
        assertThat(client.sessionIdleAt("ses_1")).isEqualTo(2500);
    }

    @Test
    void extractsOnlyAssistantText() {
        var reply = client.latestAssistantReply("ses_1", 1000).orElseThrow();
        assertThat(reply.messageId()).isEqualTo("msg_2");
        assertThat(reply.text()).isEqualTo("最终回答");
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        if (exchange.getRequestHeaders().getFirst("Authorization") == null) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
