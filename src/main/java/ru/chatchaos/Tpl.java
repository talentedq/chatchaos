package ru.chatchaos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Шаблон команды с плейсхолдерами. Разбирается один раз при загрузке файла,
 * при запуске эффекта только склеивается.
 *
 *  {player}          ник игрока-цели          {viewer}  случайный зритель (новый на каждый запуск строки)
 *  {nick}            один зритель на весь эффект      {color}   случайный цвет (число)
 *  {rand:a:b}        случайное целое           {pick:a|b|c}  случайный вариант
 *  {cycle:a|b}       по очереди (для пульсов)  {var:имя:a|b}  выбрать один раз и помнить в рамках эффекта
 *  {ring}  {sky}     случайная позиция рядом / в небе над игроком (~x ~y ~z)
 *  {neg}             случайно "-" или ничего   {pitch}  случайная высота звука
 *  {i} {i:N}         номер повтора (*N) и номер * N
 */
public final class Tpl {
    /** Состояние одного запуска эффекта (общий зритель, переменные, счётчики). */
    public static final class Ctx {
        public final String nick;
        final Map<String, String> vars = new HashMap<>();
        final Map<String, Integer> cycles = new HashMap<>();

        public Ctx(String nick) { this.nick = nick; }
    }

    private enum K { LIT, PLAYER, VIEWER, NICK, COLOR, PITCH, NEG, RING, SKY, I, RAND, PICK, CYCLE, VAR }

    private record Seg(K k, String s, int a, int b, String[] opts) {}

    private static final Random R = new Random();
    private static final Pattern PH = Pattern.compile(
            "\\{(player|viewer|nick|color|pitch|neg|ring|sky|i|i:(\\d+)|rand:(-?\\d+):(-?\\d+)"
                    + "|pick:([^{}]*)|cycle:([^{}]*)|var:([a-z0-9_]+)(?::([^{}]*))?)\\}");

    private final List<Seg> segs;
    private final boolean usesPlayer;

    private Tpl(List<Seg> segs) {
        this.segs = segs;
        boolean p = false;
        for (Seg s : segs) if (s.k() == K.PLAYER) p = true;
        this.usesPlayer = p;
    }

    public boolean usesPlayer() { return usesPlayer; }

    public static Tpl compile(String raw) {
        List<Seg> out = new ArrayList<>();
        Matcher m = PH.matcher(raw);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(new Seg(K.LIT, raw.substring(last, m.start()), 0, 0, null));
            String tok = m.group(1);
            if (m.group(2) != null) {
                out.add(new Seg(K.I, null, Integer.parseInt(m.group(2)), 0, null));
            } else if (m.group(3) != null) {
                out.add(new Seg(K.RAND, null, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), null));
            } else if (m.group(5) != null) {
                out.add(new Seg(K.PICK, null, 0, 0, m.group(5).split("\\|", -1)));
            } else if (m.group(6) != null) {
                out.add(new Seg(K.CYCLE, m.group(6), 0, 0, m.group(6).split("\\|", -1)));
            } else if (m.group(7) != null) {
                out.add(new Seg(K.VAR, m.group(7), 0, 0, m.group(8) == null ? null : m.group(8).split("\\|", -1)));
            } else {
                switch (tok) {
                    case "player" -> out.add(new Seg(K.PLAYER, null, 0, 0, null));
                    case "viewer" -> out.add(new Seg(K.VIEWER, null, 0, 0, null));
                    case "nick" -> out.add(new Seg(K.NICK, null, 0, 0, null));
                    case "color" -> out.add(new Seg(K.COLOR, null, 0, 0, null));
                    case "pitch" -> out.add(new Seg(K.PITCH, null, 0, 0, null));
                    case "neg" -> out.add(new Seg(K.NEG, null, 0, 0, null));
                    case "ring" -> out.add(new Seg(K.RING, null, 0, 0, null));
                    case "sky" -> out.add(new Seg(K.SKY, null, 0, 0, null));
                    default -> out.add(new Seg(K.I, null, 1, 0, null)); // "{i}"
                }
            }
            last = m.end();
        }
        if (last < raw.length()) out.add(new Seg(K.LIT, raw.substring(last), 0, 0, null));
        return new Tpl(out);
    }

    public String expand(String player, int i, ChaosDirector d, Ctx ctx) {
        StringBuilder sb = new StringBuilder(96);
        String viewer = null;
        for (Seg s : segs) {
            switch (s.k()) {
                case LIT -> sb.append(s.s());
                case PLAYER -> sb.append(player == null ? "" : player);
                case VIEWER -> {
                    if (viewer == null) viewer = d.chatter();
                    sb.append(viewer);
                }
                case NICK -> sb.append(ctx.nick);
                case COLOR -> sb.append(R.nextInt(0x1000000));
                case PITCH -> sb.append(String.format(Locale.ROOT, "%.2f", 0.6 + R.nextDouble() * 0.8));
                case NEG -> { if (R.nextBoolean()) sb.append('-'); }
                case RING -> sb.append('~').append(R.nextBoolean() ? "-" : "").append(3 + R.nextInt(4))
                        .append(" ~ ~").append(R.nextBoolean() ? "-" : "").append(3 + R.nextInt(4));
                case SKY -> sb.append('~').append(R.nextInt(9) - 4).append(" ~14 ~").append(R.nextInt(9) - 4);
                case I -> sb.append(i * s.a());
                case RAND -> sb.append(s.a() + R.nextInt(Math.max(1, s.b() - s.a() + 1)));
                case PICK -> sb.append(s.opts()[R.nextInt(s.opts().length)]);
                case CYCLE -> {
                    int n = ctx.cycles.merge(s.s(), 1, Integer::sum) - 1;
                    sb.append(s.opts()[n % s.opts().length]);
                }
                case VAR -> {
                    String v = ctx.vars.get(s.s());
                    if (v == null && s.opts() != null) {
                        v = s.opts()[R.nextInt(s.opts().length)];
                        ctx.vars.put(s.s(), v);
                    }
                    sb.append(v == null ? "" : v);
                }
            }
        }
        return sb.toString();
    }
}
