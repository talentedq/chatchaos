package ru.chatchaos.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import ru.chatchaos.ChatChaos;

public class ChatChaosClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ChatChaos.ID, "hud"), ChaosHud::render);

        // /chaos menu выставляет флаг на сервере; экран открываем в следующем тике клиента,
        // иначе окно чата закроется поверх него
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ChatChaos.openMenu) {
                ChatChaos.openMenu = false;
                mc.gui.setScreen(new ChaosSettingsScreen(null));
            }
        });
    }
}
