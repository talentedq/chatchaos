package ru.chatchaos.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.chatchaos.Cat;
import ru.chatchaos.ChatChaos;
import ru.chatchaos.EffectDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Список эффектов: включить/выключить, вес, тест. Изменения сразу пишутся в JSON-файл эффекта. */
public class ChaosEffectsScreen extends Screen {
    private static final double[] WEIGHTS = {0.0, 0.5, 1.0, 2.0, 3.0};

    private final Screen parent;
    private int page;
    private Cat filter; // null = все
    private int pages = 1;

    public ChaosEffectsScreen(Screen parent) {
        super(Component.literal("Эффекты"));
        this.parent = parent;
    }

    private static ChatFormatting colorOf(Cat c) {
        return switch (c) {
            case GOOD -> ChatFormatting.GREEN;
            case BAD -> ChatFormatting.RED;
            case FUN -> ChatFormatting.YELLOW;
        };
    }

    private static String fmt(double w) {
        return String.format(Locale.ROOT, "%.1f", w);
    }

    private Component rowLabel(EffectDef e) {
        String dur = e.duration > 0 ? " · " + e.duration + "с" : "";
        String s = (e.enabled ? "[x] " : "[ ] ") + e.title + dur;
        return Component.literal(s).withStyle(e.enabled ? colorOf(e.cat) : ChatFormatting.DARK_GRAY);
    }

    @Override
    protected void init() {
        List<EffectDef> list = new ArrayList<>();
        for (EffectDef e : ChatChaos.store().all()) if (filter == null || e.cat == filter) list.add(e);

        int rows = Math.max(3, (this.height - 80) / 24);
        pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));

        int x0 = this.width / 2 - 160;
        int y = 30;
        int from = page * rows, to = Math.min(list.size(), from + rows);
        for (int i = from; i < to; i++) {
            EffectDef e = list.get(i);
            addRenderableWidget(Button.builder(rowLabel(e), bt -> {
                e.enabled = !e.enabled;
                ChatChaos.store().save(e);
                bt.setMessage(rowLabel(e));
            }).bounds(x0, y, 190, 20).build());

            addRenderableWidget(Button.builder(Component.literal("вес " + fmt(e.weight)), bt -> {
                double nw = WEIGHTS[0];
                for (double w : WEIGHTS) if (w > e.weight + 1e-9) { nw = w; break; }
                e.weight = nw;
                ChatChaos.store().save(e);
                bt.setMessage(Component.literal("вес " + fmt(e.weight)));
            }).bounds(x0 + 194, y, 70, 20).build());

            addRenderableWidget(Button.builder(Component.literal(">"),
                    bt -> ChatChaos.director().triggerAsync(e.id)).bounds(x0 + 268, y, 22, 20).build());
            y += 24;
        }

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal(filterLabel()), bt -> {
            filter = filter == null ? Cat.GOOD : filter == Cat.GOOD ? Cat.BAD : filter == Cat.BAD ? Cat.FUN : null;
            page = 0;
            rebuildWidgets();
        }).bounds(x0, by, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), bt -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(x0 + 114, by, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), bt -> {
            page = Math.min(pages - 1, page + 1);
            rebuildWidgets();
        }).bounds(x0 + 142, by, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), bt -> onClose())
                .bounds(x0 + 210, by, 80, 20).build());
    }

    private String filterLabel() {
        return "Фильтр: " + (filter == null ? "все" : filter.label);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        String t = "Эффекты (файлы: config/chatchaos/effects)  ·  стр. " + (page + 1) + "/" + pages;
        g.text(this.font, t, this.width / 2 - this.font.width(t) / 2, 12, 0xFFFFC107, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
