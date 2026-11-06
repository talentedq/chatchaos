package ru.chatchaos.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.chatchaos.ChaosConfig;
import ru.chatchaos.ChatChaos;

import java.util.function.Supplier;

/** Главное меню настроек: /chaos menu */
public class ChaosSettingsScreen extends Screen {
    private static final int[] VOTE = {10, 15, 20, 30, 45, 60};
    private static final int[] PAUSE = {10, 15, 25, 40, 60, 90};

    private final Screen parent;
    private EditBox channelBox, targetBox;
    private int lx, rx, boxY;

    public ChaosSettingsScreen(Screen parent) {
        super(Component.literal("Chat Chaos"));
        this.parent = parent;
    }

    private static String onoff(boolean v) { return v ? "ВКЛ" : "ВЫКЛ"; }

    private static int next(int[] arr, int cur) {
        for (int v : arr) if (v > cur) return v;
        return arr[0];
    }

    private static String diffName(String d) {
        return switch (d) {
            case "chill" -> "Чилл";
            case "hell" -> "Ад";
            default -> "Обычная";
        };
    }

    private void btn(int x, int y, int w, Supplier<String> label, Runnable action) {
        Button b = Button.builder(Component.literal(label.get()), bt -> {
            action.run();
            ChatChaos.config().save();
            bt.setMessage(Component.literal(label.get()));
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
    }

    @Override
    protected void init() {
        ChaosConfig c = ChatChaos.config();
        int cx = this.width / 2;
        lx = cx - 155;
        rx = cx + 5;
        int w = 150, y = 34, step = 24;

        btn(lx, y, w, () -> "HUD: " + onoff(c.hud), () -> c.hud = !c.hud);
        btn(rx, y, w, () -> "Безопасный режим: " + onoff(c.safeMode), () -> c.safeMode = !c.safeMode);
        y += step;
        btn(lx, y, w, () -> "HUD: " + (c.hudSide.equals("left") ? "слева" : "справа"),
                () -> c.hudSide = c.hudSide.equals("left") ? "right" : "left");
        btn(rx, y, w, () -> "Фильтр ников: " + onoff(c.nickFilter), () -> c.nickFilter = !c.nickFilter);
        y += step;
        btn(lx, y, w, () -> "Голосование: " + c.voteSeconds + " с", () -> c.voteSeconds = next(VOTE, c.voteSeconds));
        btn(rx, y, w, () -> "Сложность: " + diffName(c.difficulty), () -> c.difficulty =
                switch (c.difficulty) {
                    case "chill" -> "normal";
                    case "normal" -> "hell";
                    default -> "chill";
                });
        y += step;
        btn(lx, y, w, () -> "Пауза: " + c.pauseSeconds + " с", () -> c.pauseSeconds = next(PAUSE, c.pauseSeconds));
        btn(rx, y, w, () -> "Ломать блоки (TNT): " + onoff(c.destructive), () -> c.destructive = !c.destructive);
        y += step;
        btn(lx, y, w, () -> "Автозапуск: " + onoff(c.autoStart), () -> c.autoStart = !c.autoStart);
        btn(rx, y, w, () -> "Случ. телепорт: " + onoff(c.allowTeleport), () -> c.allowTeleport = !c.allowTeleport);
        y += step + 14;

        // канал Twitch + подключение
        boxY = y;
        channelBox = new EditBox(this.font, lx, y, 82, 20, Component.literal("Канал"));
        channelBox.setMaxLength(25);
        channelBox.setValue(c.channel);
        addRenderableWidget(channelBox);
        addRenderableWidget(Button.builder(Component.literal("Подключить"), bt -> {
            if (!ChatChaos.connect(channelBox.getValue())) channelBox.setValue("???");
        }).bounds(lx + 84, y, 66, 20).build());

        // ник стримера
        targetBox = new EditBox(this.font, rx, y, 82, 20, Component.literal("Ник"));
        targetBox.setMaxLength(16);
        targetBox.setValue(c.target);
        addRenderableWidget(targetBox);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), bt -> {
            c.target = targetBox.getValue().trim();
            c.save();
        }).bounds(rx + 84, y, 66, 20).build());

        y += step + 8;
        addRenderableWidget(Button.builder(Component.literal("Эффекты..."),
                bt -> this.minecraft.setScreen(new ChaosEffectsScreen(this))).bounds(lx, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"),
                bt -> onClose()).bounds(rx, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        String t = "Chat Chaos - настройки";
        g.text(this.font, t, this.width / 2 - this.font.width(t) / 2, 16, 0xFFFFC107, true);
        g.text(this.font, "Канал Twitch", lx, boxY - 11, 0xFFAAAAAA, true);
        g.text(this.font, "Ник стримера (пусто = все)", rx, boxY - 11, 0xFFAAAAAA, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
