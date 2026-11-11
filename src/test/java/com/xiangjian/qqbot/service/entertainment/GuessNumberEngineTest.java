package com.xiangjian.qqbot.service.entertainment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuessNumberEngineTest {
    @Test
    void narrowsRangeAndFinishesOnCorrectGuess() {
        var state = new GuessNumberEngine.GuessNumberState(50, 0, 1, 100, false);
        var low = GuessNumberEngine.guess(state, 30);
        assertThat(low.state().min()).isEqualTo(31);
        assertThat(low.state().attempts()).isEqualTo(1);

        var correct = GuessNumberEngine.guess(low.state(), 50);
        assertThat(correct.state().finished()).isTrue();
        assertThat(correct.message()).contains("猜对了");
    }

    @Test
    void rejectsOutOfRangeGuess() {
        var state = new GuessNumberEngine.GuessNumberState(50, 0, 1, 100, false);
        assertThat(GuessNumberEngine.guess(state, 101).message()).contains("1-100");
    }
}
