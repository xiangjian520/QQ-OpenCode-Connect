package com.xiangjian.qqbot.service.entertainment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.zip.GZIPInputStream;
import java.util.stream.Collectors;

@Service
public class FunApiClient {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String USER_AGENT = "QQ-OpenCode-Connect/0.1 (+https://github.com/xiangjian520/QQ-OpenCode-Connect)";
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper;
    private volatile Map<String, String> emojiKitchen;

    public FunApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String weather(String city) {
        String target = city == null || city.isBlank() ? "北京" : city.strip();
        try {
            HttpRequest request = get("https://uapis.cn/api/v1/misc/weather?city=" + encode(target));
            JsonNode data = sendJson(request);
            String name = first(data, "city", target);
            List<String> parts = new ArrayList<>();
            parts.add(name);
            add(parts, first(data, "weather", ""));
            add(parts, data.has("temperature") ? data.path("temperature").asText() + "℃" : "");
            add(parts, first(data, "wind_direction", "") + first(data, "wind_power", ""));
            add(parts, data.has("humidity") ? "湿度 " + data.path("humidity").asText() + "%" : "");
            JsonNode alerts = data.path("alerts");
            if (alerts.isArray() && !alerts.isEmpty()) {
                StringJoiner joiner = new StringJoiner("；");
                alerts.forEach(alert -> joiner.add(first(alert, "title", alert.asText())));
                parts.add("预警 " + joiner);
            }
            return String.join(" · ", parts);
        } catch (Exception ex) {
            return "天气查询失败，请稍后重试。支持格式：/天气 城市";
        }
    }

    public String bangumiSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "请提供番剧关键词，例如 /番剧 孤独摇滚";
        }
        try {
            String body = objectMapper.writeValueAsString(new SearchBody(keyword.strip(), new Filter(List.of(2))));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.bgm.tv/v0/search/subjects?limit=5"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            JsonNode root = sendJson(request);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return "没有找到「" + keyword.strip() + "」相关番剧。";
            }
            List<String> lines = new ArrayList<>();
            lines.add("Bangumi 搜索：" + keyword.strip());
            int index = 1;
            for (JsonNode item : data) {
                String name = first(item, "name_cn", first(item, "name", "未命名"));
                String original = first(item, "name", "");
                String date = first(item, "date", "日期未知");
                String score = item.path("rating").path("score").asText("暂无");
                String id = first(item, "id", "");
                lines.add(index++ + ". " + name + (original.isBlank() || original.equals(name) ? "" : " / " + original)
                        + "\n   " + date + " · 评分 " + score + " · https://bgm.tv/subject/" + id);
            }
            return String.join("\n", lines);
        } catch (Exception ex) {
            return "Bangumi 查询失败，请稍后重试。";
        }
    }

    public String bangumiToday() {
        try {
            JsonNode root = sendJson(get("https://api.bgm.tv/calendar"));
            if (!root.isArray() || root.isEmpty()) {
                return "今天的新番日历暂时不可用。";
            }
            int index = LocalDate.now(ZONE).getDayOfWeek().getValue() - 1;
            JsonNode day = root.path(Math.min(index, root.size() - 1));
            List<String> names = new ArrayList<>();
            for (JsonNode item : day.path("items")) {
                if (names.size() >= 10) break;
                names.add(first(item, "name_cn", first(item, "name", "未命名")));
            }
            String weekday = first(day, "cn", "今日");
            return names.isEmpty() ? weekday + "暂无放送数据" : weekday + "放送：\n" + String.join("\n", names);
        } catch (Exception ex) {
            return "新番日历查询失败，请稍后重试。";
        }
    }

    public String bilibiliPopular() {
        try {
            HttpRequest request = getWithUserAgent("https://api.bilibili.com/x/web-interface/popular?ps=8&pn=1",
                    BROWSER_USER_AGENT, "Referer", "https://www.bilibili.com/", "Origin", "https://www.bilibili.com");
            JsonNode root = sendJson(request);
            if (root.path("code").asInt(0) != 0) {
                return "B 站热门接口暂时限流，请稍后重试。";
            }
            JsonNode list = root.path("data").path("list");
            if (!list.isArray() || list.isEmpty()) {
                return "暂时没有获取到 B 站热门内容。";
            }
            List<String> lines = new ArrayList<>();
            lines.add("B 站热门：");
            int index = 1;
            for (JsonNode item : list) {
                String bvid = first(item, "bvid", "");
                lines.add(index++ + ". " + first(item, "title", "未命名")
                        + " · UP " + first(item.path("owner"), "name", "未知")
                        + (bvid.isBlank() ? "" : "\n   https://www.bilibili.com/video/" + bvid));
            }
            return String.join("\n", lines);
        } catch (Exception ex) {
            return "B 站热门查询失败，请稍后重试。";
        }
    }

    public String steamSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "请提供游戏名，例如 /Steam 星露谷物语";
        }
        try {
            JsonNode root = sendJson(get("https://store.steampowered.com/api/storesearch/?term="
                    + encode(keyword.strip()) + "&l=schinese&cc=CN"));
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return "Steam API 没有返回「" + keyword.strip() + "」，可直接打开商店搜索：\n"
                        + "https://store.steampowered.com/search/?term=" + encode(keyword.strip());
            }
            List<String> lines = new ArrayList<>();
            lines.add("Steam 搜索：" + keyword.strip());
            int index = 1;
            for (JsonNode item : items) {
                if (index > 5) break;
                String name = first(item, "name", "未命名");
                String id = first(item, "id", "");
                String price = item.path("price").isMissingNode() ? "价格未知"
                        : item.path("price").path("final").asText() + " " + first(item.path("price"), "currency", "");
                lines.add(index++ + ". " + name + " · " + price
                        + (id.isBlank() ? "" : "\n   https://store.steampowered.com/app/" + id));
            }
            return String.join("\n", lines);
        } catch (Exception ex) {
            return "Steam 查询暂时不可用，可直接打开商店搜索：\n"
                    + "https://store.steampowered.com/search/?term=" + encode(keyword);
        }
    }

    public String musicSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "请提供歌曲或歌手，例如 /点歌 晴天";
        }
        try {
            HttpRequest request = getWithUserAgent("https://music.163.com/api/search/get/web?s=" + encode(keyword.strip())
                    + "&type=1&offset=0&total=true&limit=5", BROWSER_USER_AGENT,
                    "Referer", "https://music.163.com/");
            JsonNode songs = sendJson(request).path("result").path("songs");
            if (!songs.isArray() || songs.isEmpty()) {
                return "网易云没有找到「" + keyword.strip() + "」。";
            }
            List<String> lines = new ArrayList<>();
            lines.add("点歌结果：" + keyword.strip());
            int index = 1;
            for (JsonNode song : songs) {
                String artists = joinNames(song.path("artists"), "name");
                String id = first(song, "id", "");
                lines.add(index++ + ". " + first(song, "name", "未命名")
                        + (artists.isBlank() ? "" : " - " + artists)
                        + (id.isBlank() ? "" : "\n   https://music.163.com/#/song?id=" + id));
            }
            return String.join("\n", lines);
        } catch (Exception ex) {
            return "点歌查询失败，请稍后重试。";
        }
    }

    public Optional<String> emojiMixUrl(String firstEmoji, String secondEmoji) {
        String left = codePoints(firstEmoji);
        String right = codePoints(secondEmoji);
        if (left.isBlank() || right.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> combinations = emojiKitchen();
        String direct = combinations.get(left + '_' + right);
        if (direct != null) return Optional.of(direct);
        return Optional.ofNullable(combinations.get(right + '_' + left));
    }

    private Map<String, String> emojiKitchen() {
        Map<String, String> current = emojiKitchen;
        if (current != null) return current;
        synchronized (this) {
            if (emojiKitchen != null) return emojiKitchen;
            Map<String, String> loaded = new HashMap<>(180_000);
            ClassPathResource resource = new ClassPathResource("entertainment/emoji-kitchen.tsv.gz");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\t", 3);
                    if (parts.length == 3) loaded.put(parts[0] + '_' + parts[1], parts[2]);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("加载 Emoji Kitchen 数据失败", ex);
            }
            emojiKitchen = Map.copyOf(loaded);
            return emojiKitchen;
        }
    }

    private HttpRequest get(String url) {
        return get(url, new String[0]);
    }

    private HttpRequest get(String url, String... headers) {
        return getWithUserAgent(url, USER_AGENT, headers);
    }

    private HttpRequest getWithUserAgent(String url, String userAgent, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json,text/plain,*/*");
        for (int index = 0; index + 1 < headers.length; index += 2) {
            builder.header(headers[index], headers[index + 1]);
        }
        return builder.GET().build();
    }

    private JsonNode sendJson(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String codePoints(String emoji) {
        if (emoji == null || emoji.isBlank()) return "";
        return emoji.codePoints()
                .filter(code -> code != 0xfe0f && code != 0x200d)
                .mapToObj(Integer::toHexString)
                .collect(Collectors.joining("-"));
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static String first(JsonNode node, String field, String fallback) {
        if (node == null || !node.hasNonNull(field)) return fallback;
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static String joinNames(JsonNode array, String field) {
        if (!array.isArray()) return "";
        return array.findValuesAsText(field).stream().filter(value -> !value.isBlank()).collect(Collectors.joining(", "));
    }

    private record SearchBody(String keyword, Filter filter) {}
    private record Filter(List<Integer> type) {}
}
