package ru.chatchaos;

import java.util.List;

/** Неизменяемый снимок состояния для HUD (сервер пишет, клиент читает). */
public record HudState(int mode, int ticksLeft, int ticksTotal, String twitch,
                       List<Opt> options, int winner, List<Act> actives,
                       List<String> top, String last) {
    public static final int OFF = 0, WAIT = 1, VOTE = 2, RESULT = 3;

    public record Opt(String title, int votes, Cat cat) {}

    public record Act(String title, int left, int total, Cat cat) {}
}
