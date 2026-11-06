package ru.chatchaos.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.chatchaos.Cat;
import ru.chatchaos.ChaosConfig;
import ru.chatchaos.ChatChaos;
import ru.chatchaos.HudState;

import java.util.ArrayList;
import java.util.List;

/**
 * Оверлей прямо в игре: голосование с полосками, таймер, активные эффекты, топ зрителей.
 * Захватывается OBS вместе с окном игры - никаких отдельных источников не нужно.
 */
public final class ChaosHud {
    private static final int W = 210;
    private static final int BG = 0xB0101018;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GOLD = 0xFFFFC107;

    private ChaosHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        ChaosConfig cfg = ChatChaos.config();
        HudState s = ChatChaos.HUD;
        if (cfg == null || !cfg.hud || s == null) return;
        if (s.mode() == HudState.OFF && s.actives().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int x = cfg.hudSide.equals("left") ? 6 : sw - W - 6;
        int y = 6;

        // ------------------------------------------------ блок голосования / ожидания
        if (s.mode() != HudState.OFF) {
            boolean showOpts = (s.mode() == HudState.VOTE || s.mode() == HudState.RESULT) && !s.options().isEmpty();
            int h = 19 + (showOpts ? s.options().size() * 15 + 3 : 15);
            g.fill(x, y, x + W, y + h, BG);

            String title;
            String time = secs(s.ticksLeft());
            switch (s.mode()) {
                case HudState.VOTE -> title = "ГОЛОСУЙ В ЧАТЕ: 1 / 2 / 3";
                case HudState.RESULT -> title = "ВЫБРАНО!";
                default -> title = "Следующий хаос через";
            }
            g.text(font, title, x + 5, y + 4, s.mode() == HudState.RESULT ? 0xFF55FF55 : GOLD, true);
            if (s.mode() != HudState.RESULT) {
                g.text(font, time, x + W - 5 - font.width(time), y + 4, WHITE, true);
            }
            // полоска таймера
            float frac = Math.min(1f, s.ticksLeft() / (float) s.ticksTotal());
            g.fill(x + 4, y + 14, x + W - 4, y + 16, 0x55FFFFFF);
            g.fill(x + 4, y + 14, x + 4 + (int) ((W - 8) * frac), y + 16, GOLD);

            if (showOpts) {
                int total = 0;
                for (HudState.Opt o : s.options()) total += o.votes();
                for (int i = 0; i < s.options().size(); i++) {
                    HudState.Opt o = s.options().get(i);
                    int ry = y + 19 + i * 15;
                    float share = total == 0 ? 0f : o.votes() / (float) total;
                    int cx = o.cat().color;
                    // полоса голосов
                    g.fill(x + 4, ry, x + 4 + (int) ((W - 8) * share), ry + 13, (cx & 0x00FFFFFF) | 0x66000000);
                    g.fill(x + 4, ry, x + 7, ry + 13, cx);
                    if (s.mode() == HudState.RESULT && i == s.winner()) {
                        g.outline(x + 3, ry - 1, W - 6, 15, WHITE);
                    }
                    String stat = o.votes() + " (" + Math.round(share * 100) + "%)";
                    int sx = x + W - 6 - font.width(stat);
                    g.text(font, trim(font, (i + 1) + ") " + o.title(), sx - (x + 11) - 4), x + 11, ry + 3, WHITE, true);
                    g.text(font, stat, sx, ry + 3, GRAY, true);
                }
            } else {
                g.text(font, trim(font, "Последнее: " + s.last(), W - 10), x + 5, y + 20, GRAY, true);
            }
            y += h + 3;
        }

        // ------------------------------------------------ активные эффекты
        if (!s.actives().isEmpty()) {
            int n = Math.min(6, s.actives().size());
            int h = 14 + n * 16;
            g.fill(x, y, x + W, y + h, BG);
            g.text(font, "АКТИВНО", x + 5, y + 3, GRAY, true);
            for (int i = 0; i < n; i++) {
                HudState.Act a = s.actives().get(i);
                int ry = y + 14 + i * 16;
                String t = secs(a.left());
                g.text(font, trim(font, a.title(), W - 20 - font.width(t)), x + 5, ry, a.cat().color, true);
                g.text(font, t, x + W - 5 - font.width(t), ry, WHITE, true);
                float f = Math.min(1f, a.left() / (float) Math.max(1, a.total()));
                g.fill(x + 5, ry + 11, x + W - 5, ry + 13, 0x44FFFFFF);
                g.fill(x + 5, ry + 11, x + 5 + (int) ((W - 10) * f), ry + 13, a.cat().color);
            }
            y += h + 3;
        }

        // ------------------------------------------------ подвал: топ чата / статус Twitch
        List<String> foot = new ArrayList<>();
        if (!s.top().isEmpty()) foot.add("Топ чата: " + String.join(", ", s.top()));
        if (!s.twitch().startsWith("подключён")) foot.add("Twitch: " + s.twitch());
        if (!foot.isEmpty()) {
            int h = 4 + foot.size() * 11;
            g.fill(x, y, x + W, y + h, BG);
            for (int i = 0; i < foot.size(); i++) {
                boolean err = foot.get(i).startsWith("Twitch");
                g.text(font, trim(font, foot.get(i), W - 10), x + 5, y + 3 + i * 11, err ? 0xFFFF7777 : GRAY, true);
            }
        }
    }

    private static String secs(int ticks) {
        return ((ticks + 19) / 20) + " с";
    }

    private static String trim(Font font, String s, int maxWidth) {
        while (s.length() > 1 && font.width(s) > maxWidth) s = s.substring(0, s.length() - 1);
        return s;
    }
}
