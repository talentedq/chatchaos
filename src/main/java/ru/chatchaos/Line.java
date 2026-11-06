package ru.chatchaos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Одна строка эффекта из JSON.
 *   "*5 команда"      - повторить 5 раз (в шаблоне доступен {i})
 *   "@40 команда"     - выполнить через 40 тиков (можно с плейсхолдером: "@{i:14} ...")
 *   "!say текст"      - сообщение в игровой чат вместо команды
 */
public record Line(int repeat, boolean say, boolean perPlayer, Tpl tpl) {
    private static final Pattern REP = Pattern.compile("^\\*(\\d+)\\s+");

    public static Line compile(String raw) {
        String s = raw.trim();
        int rep = 1;
        Matcher m = REP.matcher(s);
        if (m.find()) {
            rep = Math.max(1, Math.min(50, Integer.parseInt(m.group(1))));
            s = s.substring(m.end());
        }
        boolean say = false;
        if (s.startsWith("!say ")) {
            say = true;
            s = s.substring(5);
        }
        Tpl t = Tpl.compile(s);
        return new Line(rep, say, t.usesPlayer(), t);
    }
}
