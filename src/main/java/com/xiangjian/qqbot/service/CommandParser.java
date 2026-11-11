package com.xiangjian.qqbot.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandParser {
    private static final Pattern COMMAND = Pattern.compile("^/([\\p{IsHan}A-Za-z]+)(?:@\\S+)?(?:\\s+(.*))?$", Pattern.DOTALL);

    private CommandParser() {}

    public static Optional<Command> parse(String text) {
        if (text == null || !text.stripLeading().startsWith("/")) {
            return Optional.empty();
        }
        Matcher matcher = COMMAND.matcher(text.strip());
        if (!matcher.matches()) {
            return Optional.of(new Command("未知", ""));
        }
        return Optional.of(new Command(matcher.group(1), matcher.group(2) == null ? "" : matcher.group(2).strip()));
    }

    public record Command(String name, String args) {}
}
