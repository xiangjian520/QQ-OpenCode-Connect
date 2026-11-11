package com.xiangjian.qqbot.service.entertainment;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class TicTacToeEngine {
    private TicTacToeEngine() {}

    public static TicTacToeState newGame() {
        return new TicTacToeState(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), false, "");
    }

    public static MoveResult move(TicTacToeState state, int position, RandomGenerator random) {
        if (state.finished()) {
            return new MoveResult(state, "这局已经结束，发送 /井字棋 开始新一局。");
        }
        if (position < 1 || position > 9) {
            return new MoveResult(state, "请输入 1-9 的位置。");
        }
        List<String> board = new ArrayList<>(state.board());
        int humanIndex = position - 1;
        if (!board.get(humanIndex).matches("[1-9]")) {
            return new MoveResult(state, "这个位置已经有棋子了。\n" + render(board));
        }
        board.set(humanIndex, "X");
        if (hasWinner(board, "X")) {
            return new MoveResult(new TicTacToeState(List.copyOf(board), true, "你"), "你赢了！\n" + render(board));
        }
        if (isFull(board)) {
            return new MoveResult(new TicTacToeState(List.copyOf(board), true, "平局"), "平局！\n" + render(board));
        }

        int botIndex = chooseBotMove(board, random);
        board.set(botIndex, "O");
        if (hasWinner(board, "O")) {
            return new MoveResult(new TicTacToeState(List.copyOf(board), true, "Bot"), "Bot 赢了，再来一局吧。\n" + render(board));
        }
        if (isFull(board)) {
            return new MoveResult(new TicTacToeState(List.copyOf(board), true, "平局"), "平局！\n" + render(board));
        }
        return new MoveResult(new TicTacToeState(List.copyOf(board), false, ""),
                "已落子。\n" + render(board) + "\n继续发送 /井字棋 位置。");
    }

    public static String render(List<String> board) {
        return """
                %s │ %s │ %s
                ──┼───┼──
                %s │ %s │ %s
                ──┼───┼──
                %s │ %s │ %s""".formatted(
                cell(board, 0), cell(board, 1), cell(board, 2),
                cell(board, 3), cell(board, 4), cell(board, 5),
                cell(board, 6), cell(board, 7), cell(board, 8)
        );
    }

    private static String cell(List<String> board, int index) {
        String value = board.get(index);
        return "X".equals(value) ? "❌" : "O".equals(value) ? "⭕" : "　" + value;
    }

    private static int chooseBotMove(List<String> board, RandomGenerator random) {
        for (int i = 0; i < 9; i++) {
            if (isFree(board, i) && winsIf(board, i, "O")) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (isFree(board, i) && winsIf(board, i, "X")) return i;
        }
        if (isFree(board, 4)) return 4;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (isFree(board, i)) free.add(i);
        return free.get(random.nextInt(free.size()));
    }

    private static boolean winsIf(List<String> board, int index, String piece) {
        List<String> copy = new ArrayList<>(board);
        copy.set(index, piece);
        return hasWinner(copy, piece);
    }

    private static boolean isFree(List<String> board, int index) {
        return board.get(index).matches("[1-9]");
    }

    private static boolean isFull(List<String> board) {
        return board.stream().noneMatch(value -> value.matches("[1-9]"));
    }

    public static boolean hasWinner(List<String> b, String p) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] line : lines) {
            if (p.equals(b.get(line[0])) && p.equals(b.get(line[1])) && p.equals(b.get(line[2]))) return true;
        }
        return false;
    }

    public record TicTacToeState(List<String> board, boolean finished, String winner) {}
    public record MoveResult(TicTacToeState state, String message) {}
}
