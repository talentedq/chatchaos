package ru.chatchaos;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Эффект, загруженный из config/chatchaos/effects/<id>.json (id = имя файла). */
public final class EffectDef {
    public final String id;
    public String title;
    public Cat cat = Cat.FUN;
    public String group = "";
    public int duration;               // секунды, 0 = мгновенный
    public volatile double weight = 1; // 0 = никогда не выпадает
    public volatile boolean enabled = true;
    public boolean danger;             // можно убить: исключается в безопасном режиме при низком здоровье
    public String needs = "";          // "destructive" | "teleport" - нужна галочка в настройках
    public int pulseEvery;             // тики между вызовами pulse

    public List<String> rawStart = new ArrayList<>(), rawPulse = new ArrayList<>(), rawEnd = new ArrayList<>();
    public List<Line> start = List.of(), pulse = List.of(), end = List.of();

    public EffectDef(String id) { this.id = id; }

    public static EffectDef fromJson(String id, JsonObject o) {
        EffectDef e = new EffectDef(id);
        e.title = str(o, "title", id);
        try {
            e.cat = Cat.valueOf(str(o, "category", "FUN").toUpperCase());
        } catch (IllegalArgumentException ex) {
            e.cat = Cat.FUN;
        }
        e.group = str(o, "group", "");
        e.duration = Math.max(0, (int) num(o, "duration", 0));
        e.weight = Math.max(0, num(o, "weight", 1));
        e.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        e.danger = o.has("danger") && o.get("danger").getAsBoolean();
        e.needs = str(o, "needs", "");
        e.pulseEvery = Math.max(0, (int) num(o, "pulseEvery", 0));
        e.rawStart = list(o, "start");
        e.rawPulse = list(o, "pulse");
        e.rawEnd = list(o, "end");
        e.compile();
        return e;
    }

    public void compile() {
        start = compileAll(rawStart);
        pulse = compileAll(rawPulse);
        end = compileAll(rawEnd);
    }

    private static List<Line> compileAll(List<String> raw) {
        List<Line> out = new ArrayList<>();
        for (String s : raw) if (!s.isBlank()) out.add(Line.compile(s));
        return out;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("category", cat.name());
        o.addProperty("group", group);
        o.addProperty("duration", duration);
        o.addProperty("weight", weight);
        o.addProperty("enabled", enabled);
        o.addProperty("danger", danger);
        o.addProperty("needs", needs);
        o.addProperty("pulseEvery", pulseEvery);
        o.add("start", arr(rawStart));
        o.add("pulse", arr(rawPulse));
        o.add("end", arr(rawEnd));
        return o;
    }

    private static JsonArray arr(List<String> l) {
        JsonArray a = new JsonArray();
        for (String s : l) a.add(s);
        return a;
    }

    private static List<String> list(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o.has(k) && o.get(k).isJsonArray()) {
            for (var el : o.getAsJsonArray(k)) out.add(el.getAsString());
        }
        return out;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static double num(JsonObject o, String k, double def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
    }
}
