package com.xiangjian.qqbot.service.entertainment;

import java.util.random.RandomGenerator;

public final class GuessNumberEngine {
    private GuessNumberEngine() {}

    public static GuessNumberState newGame(RandomGenerator random) {
        return new GuessNumberState(random.nextInt(100) + 1, 0, 1, 100, false);
    }

    public static GuessResult guess(GuessNumberState state, int guess) {
        if (guess < 1 || guess > 100) {
            return new GuessResult(state, "请输入 1-100 之间的数字。");
        }
        int attempts = state.attempts() + 1;
        if (guess == state.target()) {
            return new GuessResult(new GuessNumberState(state.target(), attempts, state.min(), state.max(), true),
                    "猜对了！答案就是 " + state.target() + "，共用了 " + attempts + " 次。");
        }
        int min = state.min();
        int max = state.max();
        if (guess < state.target()) {
            min = Math.max(min, guess + 1);
            return new GuessResult(new GuessNumberState(state.target(), attempts, min, max, false),
                    "太小了，范围更新为 " + min + "-" + max + "。");
        }
        max = Math.min(max, guess - 1);
        return new GuessResult(new GuessNumberState(state.target(), attempts, min, max, false),
                "太大了，范围更新为 " + min + "-" + max + "。");
    }

    public record GuessNumberState(int target, int attempts, int min, int max, boolean finished) {}
    public record GuessResult(GuessNumberState state, String message) {}
}
