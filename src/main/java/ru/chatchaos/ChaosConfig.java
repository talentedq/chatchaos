package ru.chatchaos;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Настройки в config/chatchaos.properties */
public final class ChaosConfig {
    public String channel = "";
    public String target = "";
    public int voteSeconds = 20;
    public int pauseSeconds = 25;
    public boolean allowTeleport = false;  // эффект «случайный телепорт»
    public boolean destructive = false;    // эффекты, ломающие блоки (дождь из TNT)
    public boolean autoStart = true;
    public boolean hud = true;             // оверлей прямо в игре
    public String hudSide = "right";       // left / right
    public boolean safeMode = true;        // при <= 4 сердцах не предлагать опасные эффекты
    public String difficulty = "normal";   // chill / normal / hell
    public boolean nickFilter = true;      // прятать неприятные ники зрителей
    public String blockedWords = "nazi,hitler,porn,rape,fuck,shit"; // через запятую, дополняй сам

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chatchaos.properties");
    }

    public static ChaosConfig load() {
        ChaosConfig c = new ChaosConfig();
        Properties p = new Properties();
        Path f = file();
        if (Files.exists(f)) {
            try (var in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException e) {
                ChatChaos.LOG.warn("Не удалось прочитать конфиг", e);
            }
        }
        c.channel = p.getProperty("channel", "").trim().toLowerCase().replace("#", "");
        c.target = p.getProperty("target", "").trim();
        c.voteSeconds = num(p.getProperty("voteSeconds"), 20, 5, 120);
        c.pauseSeconds = num(p.getProperty("pauseSeconds"), 25, 5, 600);
        c.allowTeleport = Boolean.parseBoolean(p.getProperty("allowTeleport", "false"));
        c.destructive = Boolean.parseBoolean(p.getProperty("destructive", "false"));
        c.autoStart = Boolean.parseBoolean(p.getProperty("autoStart", "true"));
        c.hud = Boolean.parseBoolean(p.getProperty("hud", "true"));
        c.hudSide = p.getProperty("hudSide", "right").trim().toLowerCase().equals("left") ? "left" : "right";
        c.safeMode = Boolean.parseBoolean(p.getProperty("safeMode", "true"));
        String d = p.getProperty("difficulty", "normal").trim().toLowerCase();
        c.difficulty = d.equals("chill") || d.equals("hell") ? d : "normal";
        c.nickFilter = Boolean.parseBoolean(p.getProperty("nickFilter", "true"));
        c.blockedWords = p.getProperty("blockedWords", c.blockedWords).trim();
        c.save();
        return c;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("channel", channel);
        p.setProperty("target", target);
        p.setProperty("voteSeconds", String.valueOf(voteSeconds));
        p.setProperty("pauseSeconds", String.valueOf(pauseSeconds));
        p.setProperty("allowTeleport", String.valueOf(allowTeleport));
        p.setProperty("destructive", String.valueOf(destructive));
        p.setProperty("autoStart", String.valueOf(autoStart));
        p.setProperty("hud", String.valueOf(hud));
        p.setProperty("hudSide", hudSide);
        p.setProperty("safeMode", String.valueOf(safeMode));
        p.setProperty("difficulty", difficulty);
        p.setProperty("nickFilter", String.valueOf(nickFilter));
        p.setProperty("blockedWords", blockedWords);
        try (var out = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(out, "Chat Chaos. channel = канал Twitch; target = ник стримера (пусто = все игроки)");
        } catch (IOException e) {
            ChatChaos.LOG.warn("Не удалось сохранить конфиг", e);
        }
    }

    private static int num(String s, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(s.trim())));
        } catch (Exception e) {
            return def;
        }
    }
}
