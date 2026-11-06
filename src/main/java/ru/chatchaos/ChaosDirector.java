package ru.chatchaos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;

/**
 * Режиссёр хаоса: пауза -> голосование чата (1/2/3) -> эффект -> пауза...
 * Эффекты приходят из EffectStore (JSON-файлы). Всё выполняется в потоке сервера.
 */
public final class ChaosDirector {
    private enum Phase { OFF, WAITING, VOTING }

    private static final class Task {
        int ticks;
        final Runnable run;
        Task(int ticks, Runnable run) { this.ticks = ticks; this.run = run; }
    }

    private static final class Active {
        final EffectDef def;
        final Tpl.Ctx ctx;
        final int total;
        int left;
        Active(EffectDef def, Tpl.Ctx ctx) {
            this.def = def;
            this.ctx = ctx;
            this.total = def.duration * 20;
            this.left = total;
        }
    }

    private static final int RESULT_TICKS = 80;

    private final ChaosConfig cfg;
    private final TwitchChat chat;
    private final EffectStore store;
    private final Random rnd = new Random();
    private final List<Task> tasks = new ArrayList<>();
    private final List<Active> actives = new ArrayList<>();
    private final Map<String, Integer> votes = new HashMap<>();
    private final Map<String, Integer> voteTotals = new HashMap<>();
    private final Deque<String> chatters = new ArrayDeque<>();
    private final Deque<String> history = new ArrayDeque<>();

    private List<EffectDef> options = new ArrayList<>();
    private List<String> top = List.of();
    private List<HudState.Opt> resultOpts = List.of();
    private int resultWinner = -1;
    private int resultTicks;
    private int viewerCd;
    private MinecraftServer server;
    private Phase phase = Phase.OFF;
    private int ticksLeft;
    private String lastEvent = "-";

    public ChaosDirector(ChaosConfig cfg, TwitchChat chat, EffectStore store) {
        this.cfg = cfg;
        this.chat = chat;
        this.store = store;
    }

    public void attach(MinecraftServer s) { this.server = s; }

    /** Выполнить в потоке сервера (безопасно вызывать из меню на клиенте). */
    public void onServer(Runnable r) {
        MinecraftServer s = server;
        if (s != null) s.execute(r);
    }

    // ------------------------------------------------------------ управление

    public void start() {
        if (server == null) return;
        phase = Phase.WAITING;
        ticksLeft = 10 * 20;
        broadcast("Chat Chaos запущен! Первое голосование через 10 секунд.", ChatFormatting.GOLD);
    }

    public void stop() {
        phase = Phase.OFF;
        publish();
    }

    public void skip() {
        if (phase == Phase.WAITING) openVote();
        else if (phase == Phase.VOTING) closeVote();
    }

    /** При выходе из мира: вернуть размер, гравитацию и т.п. */
    public void shutdown() {
        endAllActives();
        tasks.clear();
        phase = Phase.OFF;
        ChatChaos.HUD = null;
    }

    public void resetAll() {
        endAllActives();
        tasks.clear();
        each(p -> {
            cmd("attribute " + p + " minecraft:scale base set 1");
            cmd("attribute " + p + " minecraft:gravity base set 0.08");
            cmd("attribute " + p + " minecraft:max_health base set 20");
            cmd("effect clear " + p);
        });
        publish();
    }

    public void clearActives() {
        endAllActives();
        publish();
    }

    private void endAllActives() {
        for (Active a : new ArrayList<>(actives)) endActive(a);
        actives.clear();
    }

    private void endActive(Active a) {
        safe(() -> runLines(a.def.end, a.ctx));
    }

    public boolean trigger(String id) {
        EffectDef e = store.get(id);
        if (e == null) return false;
        apply(e);
        return true;
    }

    public void triggerAsync(String id) { onServer(() -> trigger(id)); }

    public String statusLine() {
        return "Twitch: " + chat.status + " | режим: " + phase + " | эффектов: " + store.all().size()
                + " | последний: " + lastEvent;
    }

    // ------------------------------------------------------------ такт

    public void tick(MinecraftServer s) {
        this.server = s;

        TwitchChat.Msg m;
        int guard = 0;
        while ((m = chat.poll()) != null && guard++ < 500) onChat(m.user(), m.text());

        if (viewerCd > 0) viewerCd--;
        if (resultTicks > 0) resultTicks--;

        if (!tasks.isEmpty()) {
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
                Task t = it.next();
                if (--t.ticks <= 0) {
                    due.add(t);
                    it.remove();
                }
            }
            for (Task t : due) safe(t.run);
        }

        for (Iterator<Active> it = actives.iterator(); it.hasNext(); ) {
            Active a = it.next();
            EffectDef e = a.def;
            a.left--;
            if (e.pulseEvery > 0 && !e.pulse.isEmpty() && a.left > 0 && (a.total - a.left) % e.pulseEvery == 0) {
                safe(() -> runLines(e.pulse, a.ctx));
            }
            if (a.left <= 0) {
                it.remove();
                endActive(a);
            }
        }

        if (phase != Phase.OFF) {
            ticksLeft--;
            if (ticksLeft <= 0) {
                if (phase == Phase.WAITING) openVote(); else closeVote();
            }
        }
        publish();
    }

    // ------------------------------------------------------------ чат и голосование

    private String safeName(String user) {
        if (user.length() > 20) user = user.substring(0, 20);
        if (!cfg.nickFilter) return user;
        String n = user.toLowerCase().replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace("_", "");
        for (String w : cfg.blockedWords.split(",")) {
            w = w.trim().toLowerCase();
            if (!w.isEmpty() && n.contains(w)) return "Viewer" + (100 + (user.hashCode() & 0x7fffffff) % 900);
        }
        return user;
    }

    private void onChat(String rawUser, String text) {
        String clean = rawUser.replaceAll("[^A-Za-z0-9_]", "");
        if (clean.isEmpty()) return;
        String user = safeName(clean);
        chatters.remove(user);
        chatters.addFirst(user);
        while (chatters.size() > 40) chatters.removeLast();

        String t = text.trim().toLowerCase();
        if (t.startsWith("!")) t = t.substring(1);

        if (phase == Phase.VOTING && t.length() == 1 && t.charAt(0) >= '1' && t.charAt(0) <= '0' + options.size()) {
            votes.put(user, t.charAt(0) - '1');
            return;
        }
        if (text.trim().startsWith("!")) viewerCommand(user, t.split("\\s+")[0]);
    }

    /** Бесплатные зрительские команды вне голосования. Общий кулдаун 15 секунд. */
    private void viewerCommand(String user, String word) {
        if (viewerCd > 0 || server == null) return;
        if (word.equals("boo")) {
            each(p -> cmd("execute at " + p + " run playsound minecraft:entity.creeper.primed hostile " + p + " ~2 ~ ~-2 1 1"));
        } else if (word.equals("cheer")) {
            each(p -> {
                cmd("execute at " + p + " run particle minecraft:heart ~ ~1.5 ~ 0.6 0.5 0.6 0 14");
                cmd("execute at " + p + " run playsound minecraft:entity.player.levelup player " + p + " ~ ~ ~ 1 1");
            });
        } else if (word.equals("oops")) {
            each(p -> cmd("execute at " + p + " run playsound minecraft:entity.villager.no neutral " + p + " ~ ~ ~ 1 1"));
        } else {
            return;
        }
        viewerCd = 15 * 20;
        broadcast(user + ": !" + word, ChatFormatting.GRAY);
    }

    private void openVote() {
        options = pickOptions();
        if (options.size() < 3) {
            phase = Phase.OFF;
            broadcast("Мало включённых эффектов (нужно минимум 3). Открой /chaos menu.", ChatFormatting.RED);
            return;
        }
        votes.clear();
        phase = Phase.VOTING;
        ticksLeft = cfg.voteSeconds * 20;
        resultTicks = 0;

        broadcast("ГОЛОСОВАНИЕ! Пишите в чате Twitch 1, 2 или 3:", ChatFormatting.GOLD);
        for (int i = 0; i < options.size(); i++) {
            broadcast("  " + (i + 1) + ") " + options.get(i).title + "  [" + options.get(i).cat.label + "]",
                    ChatFormatting.YELLOW);
        }
        each(p -> cmd("playsound minecraft:block.note_block.bell master " + p + " ~ ~ ~ 1 1.2"));
    }

    private boolean usable(EffectDef e) {
        if (!e.enabled || e.weight <= 0) return false;
        return switch (e.needs) {
            case "destructive" -> cfg.destructive;
            case "teleport" -> cfg.allowTeleport;
            default -> true;
        };
    }

    private boolean lowHealth() {
        if (server == null) return false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String n = p.getName().getString();
            if ((cfg.target.isEmpty() || cfg.target.equalsIgnoreCase(n)) && p.getHealth() <= 8f) return true;
        }
        return false;
    }

    private Cat drawCat() {
        double[] w = switch (cfg.difficulty) {
            case "chill" -> new double[]{1.5, 0.5, 1};
            case "hell" -> new double[]{0.6, 1.8, 1};
            default -> new double[]{1, 1, 1};
        };
        double r = rnd.nextDouble() * (w[0] + w[1] + w[2]);
        return r < w[0] ? Cat.GOOD : r < w[0] + w[1] ? Cat.BAD : Cat.FUN;
    }

    private EffectDef weighted(List<EffectDef> c) {
        double t = 0;
        for (EffectDef e : c) t += e.weight;
        double r = rnd.nextDouble() * t;
        for (EffectDef e : c) {
            r -= e.weight;
            if (r <= 0) return e;
        }
        return c.get(c.size() - 1);
    }

    /** По одному варианту из разных категорий (с учётом сложности), без недавних повторов, с весами. */
    private List<EffectDef> pickOptions() {
        Set<String> blocked = new HashSet<>(history);
        Set<String> groups = new HashSet<>();
        for (Active a : actives) {
            blocked.add(a.def.id);
            if (!a.def.group.isEmpty()) groups.add(a.def.group);
        }
        boolean low = cfg.safeMode && lowHealth();

        List<EffectDef> usableAll = new ArrayList<>();
        for (EffectDef e : store.all()) if (usable(e) && !(low && e.danger)) usableAll.add(e);

        List<EffectDef> avail = new ArrayList<>();
        for (EffectDef e : usableAll) {
            if (blocked.contains(e.id)) continue;
            if (!e.group.isEmpty() && groups.contains(e.group)) continue;
            avail.add(e);
        }
        if (avail.size() < 6) avail = new ArrayList<>(usableAll);

        List<EffectDef> res = new ArrayList<>();
        for (Cat base : Cat.values()) {
            int chance = 35;
            if (cfg.difficulty.equals("chill") && base == Cat.BAD) chance = 60;
            if (cfg.difficulty.equals("hell") && base == Cat.GOOD) chance = 60;
            Cat c = rnd.nextInt(100) < chance ? drawCat() : base;
            List<EffectDef> cand = new ArrayList<>();
            for (EffectDef e : avail) if (e.cat == c && !res.contains(e)) cand.add(e);
            if (!cand.isEmpty()) res.add(weighted(cand));
        }
        while (res.size() < 3) {
            List<EffectDef> cand = new ArrayList<>();
            for (EffectDef e : avail) if (!res.contains(e)) cand.add(e);
            if (cand.isEmpty()) break;
            res.add(weighted(cand));
        }
        Collections.shuffle(res, rnd);
        return res;
    }

    private void closeVote() {
        int[] counts = counts();
        int total = 0, max = -1;
        for (int c : counts) { total += c; max = Math.max(max, c); }

        int winner;
        if (total == 0) {
            winner = rnd.nextInt(options.size());
            broadcast("Чат молчит - эффект выберет судьба!", ChatFormatting.GRAY);
        } else {
            List<Integer> best = new ArrayList<>();
            for (int i = 0; i < counts.length; i++) if (counts[i] == max) best.add(i);
            winner = best.get(rnd.nextInt(best.size()));
        }

        for (String u : votes.keySet()) voteTotals.merge(u, 1, Integer::sum);
        top = voteTotals.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(en -> en.getKey() + " (" + en.getValue() + ")")
                .toList();

        resultOpts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            resultOpts.add(new HudState.Opt(options.get(i).title, counts[i], options.get(i).cat));
        }
        resultWinner = winner;
        resultTicks = RESULT_TICKS;

        EffectDef e = options.get(winner);
        phase = Phase.WAITING;
        ticksLeft = cfg.pauseSeconds * 20;
        apply(e);
    }

    private int[] counts() {
        int[] c = new int[options.size()];
        for (int v : votes.values()) if (v >= 0 && v < c.length) c[v]++;
        return c;
    }

    private void apply(EffectDef e) {
        lastEvent = e.title;
        history.addLast(e.id);
        while (history.size() > 8) history.removeFirst();

        // эффект той же группы заменяет предыдущий (два размера сразу не бывает)
        if (!e.group.isEmpty()) {
            for (Active a : new ArrayList<>(actives)) {
                if (a.def.group.equals(e.group)) {
                    actives.remove(a);
                    endActive(a);
                }
            }
        }

        broadcast("ЭФФЕКТ: " + e.title, ChatFormatting.LIGHT_PURPLE);
        each(p -> {
            cmd("title " + p + " subtitle " + json("по воле чата Twitch", "yellow"));
            cmd("title " + p + " title " + json(e.title, "gold"));
            cmd("playsound minecraft:ui.toast.challenge_complete master " + p + " ~ ~ ~ 1 1");
        });

        Tpl.Ctx ctx = new Tpl.Ctx(chatter());
        safe(() -> runLines(e.start, ctx));
        if (e.duration > 0) actives.add(new Active(e, ctx));
    }

    // ------------------------------------------------------------ движок строк

    public void runLines(List<Line> lines, Tpl.Ctx ctx) {
        for (Line ln : lines) {
            for (int i = 0; i < ln.repeat(); i++) {
                if (ln.perPlayer()) {
                    for (String p : players()) emit(ln, ctx, p, i);
                } else {
                    emit(ln, ctx, null, i);
                }
            }
        }
    }

    private void emit(Line ln, Tpl.Ctx ctx, String player, int i) {
        String s = ln.tpl().expand(player, i, this, ctx).trim();
        if (s.isEmpty()) return;
        if (ln.say()) {
            broadcast(s, ChatFormatting.AQUA);
            return;
        }
        if (s.startsWith("@")) {
            int sp = s.indexOf(' ');
            if (sp > 1) {
                try {
                    int delay = Integer.parseInt(s.substring(1, sp));
                    final String body = s.substring(sp + 1).trim();
                    if (delay > 0) later(delay, () -> cmd(body));
                    else cmd(body);
                    return;
                } catch (NumberFormatException ignored) {
                    // не задержка - выполняем как есть
                }
            }
        }
        cmd(s);
    }

    // ------------------------------------------------------------ HUD

    private void publish() {
        List<HudState.Opt> opts = new ArrayList<>();
        int mode;
        int left = ticksLeft;
        int total = 1;
        int winner = -1;
        if (phase == Phase.VOTING) {
            mode = HudState.VOTE;
            total = cfg.voteSeconds * 20;
            int[] c = counts();
            for (int i = 0; i < options.size(); i++) {
                opts.add(new HudState.Opt(options.get(i).title, c[i], options.get(i).cat));
            }
        } else if (resultTicks > 0) {
            mode = HudState.RESULT;
            left = resultTicks;
            total = RESULT_TICKS;
            opts = new ArrayList<>(resultOpts);
            winner = resultWinner;
        } else if (phase == Phase.WAITING) {
            mode = HudState.WAIT;
            total = cfg.pauseSeconds * 20;
        } else {
            mode = HudState.OFF;
        }
        List<HudState.Act> acts = new ArrayList<>();
        for (Active a : actives) acts.add(new HudState.Act(a.def.title, a.left, a.total, a.def.cat));
        ChatChaos.HUD = new HudState(mode, Math.max(0, left), Math.max(1, total), chat.status,
                opts, winner, acts, top, lastEvent);
    }

    // ------------------------------------------------------------ утилиты

    public List<String> players() {
        List<String> out = new ArrayList<>();
        if (server == null) return out;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String n = p.getName().getString();
            if (cfg.target.isEmpty() || cfg.target.equalsIgnoreCase(n)) out.add(n);
        }
        return out;
    }

    public void each(Consumer<String> f) {
        for (String p : players()) f.accept(p);
    }

    public void cmd(String command) {
        if (server == null) return;
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), command);
        } catch (Exception ex) {
            ChatChaos.LOG.warn("Команда не выполнилась: {}", command, ex);
        }
    }

    /** Ник случайного недавнего зрителя (уже прошёл фильтр). */
    public String chatter() {
        if (chatters.isEmpty()) return "Viewer" + (100 + rnd.nextInt(900));
        List<String> l = new ArrayList<>(chatters);
        return l.get(rnd.nextInt(l.size()));
    }

    public void later(int ticks, Runnable r) { tasks.add(new Task(ticks, r)); }

    private void broadcast(String text, ChatFormatting color) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Хаос] " + text).withStyle(color), false);
    }

    private static String json(String text, String color) {
        String esc = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"text\":\"" + esc + "\",\"color\":\"" + color + "\"}";
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            ChatChaos.LOG.warn("Ошибка эффекта", e);
        }
    }
}
