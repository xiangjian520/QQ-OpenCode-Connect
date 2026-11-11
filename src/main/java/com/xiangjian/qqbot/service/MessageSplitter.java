package com.xiangjian.qqbot.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MessageSplitter {
    public List<String> split(String text, int maxChars) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int limit = Math.max(100, maxChars);
        while (normalized.length() > limit) {
            int cut = bestCut(normalized, limit);
            if (cut < limit / 3) {
                cut = limit;
            }
            if (cut > 0 && cut < normalized.length() && Character.isHighSurrogate(normalized.charAt(cut - 1))) {
                cut--;
            }
            String chunk = normalized.substring(0, cut).strip();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            normalized = normalized.substring(cut).strip();
        }
        if (!normalized.isBlank()) {
            chunks.add(normalized);
        }
        return chunks;
    }

    private int bestCut(String text, int limit) {
        int min = Math.max(1, limit / 3);
        int best = -1;
        for (String delimiter : new String[]{"\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", "；", "; "}) {
            int index = text.lastIndexOf(delimiter, limit);
            if (index >= min) {
                best = Math.max(best, index + delimiter.length());
            }
        }
        return best > 0 ? best : limit;
    }
}
