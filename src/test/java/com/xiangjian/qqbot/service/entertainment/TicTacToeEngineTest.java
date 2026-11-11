package com.xiangjian.qqbot.service.entertainment;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TicTacToeEngineTest {
    @Test
    void startsWithNineOpenPositions() {
        var state = TicTacToeEngine.newGame();
        assertThat(state.board()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThat(state.finished()).isFalse();
    }

    @Test
    void humanMoveIsPlacedAndBotResponds() {
        var result = TicTacToeEngine.move(TicTacToeEngine.newGame(), 5, new Random(1));
        assertThat(result.state().board()).contains("X", "O");
        assertThat(result.message()).contains("落子");
    }

    @Test
    void rejectsOccupiedPosition() {
        var state = TicTacToeEngine.newGame();
        var first = TicTacToeEngine.move(state, 1, new Random(1)).state();
        var result = TicTacToeEngine.move(first, 1, new Random(1));
        assertThat(result.message()).contains("已经有棋子");
    }

    @Test
    void detectsWinningLine() {
        assertThat(TicTacToeEngine.hasWinner(
                java.util.List.of("X", "X", "X", "4", "5", "6", "7", "8", "9"), "X")).isTrue();
    }
}
