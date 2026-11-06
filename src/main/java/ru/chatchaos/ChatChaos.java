package ru.chatchaos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChatChaos implements ModInitializer {
    public static final String ID = "chatchaos";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    /** Снимок для HUD (клиент читает напрямую: одиночная игра / хост LAN). */
    public static volatile HudState HUD;
    /** /chaos menu выставляет флаг, клиент открывает экран настроек. */
    public static volatile boolean openMenu;

    private static ChaosConfig config;
    private static TwitchChat chat;
    private static EffectStore store;
    private static ChaosDirector director;

    public static ChaosConfig config() { return config; }
    public static EffectStore store() { return store; }
    public static ChaosDirector director() { return director; }

    /** Подключиться к каналу и запустить (можно звать с любого потока). */
    public static boolean connect(String channel) {
        String ch = channel.trim().toLowerCase().replace("#", "");
        if (!ch.matches("[a-z0-9_]{3,25}")) return false;
        config.channel = ch;
        config.save();
        chat.connect(ch);
        director.onServer(director::start);
        return true;
    }

    @Override
    public void onInitialize() {
        config = ChaosConfig.load();
        store = new EffectStore();
        store.load();
        chat = new TwitchChat();
        director = new ChaosDirector(config, chat, store);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            director.attach(server);
            if (config.autoStart && !config.channel.isEmpty()) {
                chat.connect(config.channel);
                director.start();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            director.shutdown();
            chat.disconnect();
        });
        ServerTickEvents.END_SERVER_TICK.register(director::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        LOG.info("Chat Chaos загружен. Настройки: /chaos menu");
    }

    private static void ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
    }

    private static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (EffectDef e : store.all()) l.add(e.id);
        return l;
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("chaos")
                // доступно владельцу одиночной игры/LAN и консоли сервера
                .requires(src -> src.getServer() == null || src.getServer().isSingleplayer() || src.getEntity() == null)

                .then(Commands.literal("menu").executes(ctx -> {
                    openMenu = true;
                    return 1;
                }))

                .then(Commands.literal("connect")
                        .then(Commands.argument("channel", StringArgumentType.word())
                                .executes(ctx -> {
                                    String ch = StringArgumentType.getString(ctx, "channel");
                                    if (!connect(ch)) {
                                        ctx.getSource().sendFailure(Component.literal("Неверное имя канала"));
                                        return 0;
                                    }
                                    ok(ctx.getSource(), "Подключаюсь к чату Twitch: #" + config.channel);
                                    return 1;
                                })))

                .then(Commands.literal("disconnect").executes(ctx -> {
                    chat.disconnect();
                    director.stop();
                    ok(ctx.getSource(), "Отключено.");
                    return 1;
                }))
                .then(Commands.literal("start").executes(ctx -> {
                    director.start();
                    return 1;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    director.stop();
                    ok(ctx.getSource(), "Остановлено (активные эффекты доиграют).");
                    return 1;
                }))
                .then(Commands.literal("skip").executes(ctx -> {
                    director.skip();
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    director.clearActives();
                    ok(ctx.getSource(), "Активные эффекты завершены.");
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    director.resetAll();
                    ok(ctx.getSource(), "Размер, гравитация, здоровье и эффекты сброшены.");
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    store.load();
                    ok(ctx.getSource(), "Эффекты перезагружены: " + store.all().size());
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ok(ctx.getSource(), director.statusLine());
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    ok(ctx.getSource(), "Эффекты (файлы в " + store.directory() + "): " + String.join(", ", ids()));
                    return 1;
                }))

                .then(Commands.literal("test")
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids(), b))
                                .executes(ctx -> {
                                    if (!director.trigger(StringArgumentType.getString(ctx, "effect"))) {
                                        ctx.getSource().sendFailure(Component.literal("Нет такого эффекта. /chaos list"));
                                        return 0;
                                    }
                                    return 1;
                                })))

                .then(Commands.literal("fake")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            chat.inject(StringArgumentType.getString(ctx, "user"),
                                                    StringArgumentType.getString(ctx, "text"));
                                            return 1;
                                        }))))
        );
    }
}
