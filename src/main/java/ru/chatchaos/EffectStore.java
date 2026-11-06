package ru.chatchaos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Загрузка эффектов из config/chatchaos/effects/*.json. Имя файла = id эффекта.
 * При первом запуске (и для новых эффектов в новых версиях мода) файлы копируются из jar.
 * Если ты удалил файл - он не вернётся (список установленных хранится в installed.txt).
 */
public final class EffectStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("chatchaos");
    private final Path dir = root.resolve("effects");
    private volatile Map<String, EffectDef> all = new LinkedHashMap<>();

    public Collection<EffectDef> all() { return all.values(); }

    public EffectDef get(String id) { return all.get(id.toLowerCase()); }

    public Path directory() { return dir; }

    public void load() {
        Map<String, EffectDef> fresh = new LinkedHashMap<>();
        try {
            installDefaults();
            List<Path> files = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
            }
            for (Path f : files) {
                String name = f.getFileName().toString();
                String id = name.substring(0, name.length() - 5).toLowerCase();
                if (!id.matches("[a-z0-9_]+")) {
                    ChatChaos.LOG.warn("Пропускаю {}: имя файла должно быть из a-z, 0-9, _", name);
                    continue;
                }
                try {
                    JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                    fresh.put(id, EffectDef.fromJson(id, o));
                } catch (Exception e) {
                    ChatChaos.LOG.warn("Ошибка в файле эффекта {}: {}", name, e.toString());
                }
            }
        } catch (IOException e) {
            ChatChaos.LOG.warn("Не удалось загрузить эффекты", e);
        }
        all = fresh;
        ChatChaos.LOG.info("Загружено эффектов: {}", fresh.size());
    }

    public void save(EffectDef e) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(e.id + ".json"), GSON.toJson(e.toJson()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ChatChaos.LOG.warn("Не удалось сохранить эффект {}", e.id, ex);
        }
    }

    private void installDefaults() throws IOException {
        Files.createDirectories(dir);
        Path marker = root.resolve("installed.txt");
        Set<String> done = new HashSet<>();
        if (Files.exists(marker)) done.addAll(Files.readAllLines(marker, StandardCharsets.UTF_8));
        String index = resource("/chatchaos_defaults/index.txt");
        if (index == null) return;
        for (String id : index.split("\\R")) {
            id = id.trim();
            if (id.isEmpty() || done.contains(id)) continue;
            Path f = dir.resolve(id + ".json");
            if (!Files.exists(f)) {
                String json = resource("/chatchaos_defaults/" + id + ".json");
                if (json != null) Files.writeString(f, json, StandardCharsets.UTF_8);
            }
            done.add(id);
        }
        Files.write(marker, done, StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = EffectStore.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
