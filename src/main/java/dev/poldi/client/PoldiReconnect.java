package dev.poldi.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Auto-Reconnect nach einem Verbindungsabbruch (Countdown, abbrechbar) + Beitreten-Hilfe fuer das Hauptmenue. */
public final class PoldiReconnect {
    private static final int MAX_TRIES = 10;
    private static String lastName, lastIp;
    private static int tries = 0;
    private static boolean cancelled = false;

    private PoldiReconnect() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData si = client.getCurrentServer();
            if (si != null) { lastName = si.name; lastIp = si.ip; }
            tries = 0; cancelled = false;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen)) return;
            PoldiConfig cfg = PoldiConfig.get();
            if (!cfg.autoReconnect || lastIp == null || cancelled || tries >= MAX_TRIES) return;
            final long[] target = {System.currentTimeMillis() + Math.max(2, cfg.reconnectSec) * 1000L};
            final boolean[] fired = {false};
            Button btn = Button.builder(Component.literal("Auto-Reconnect …"), b -> {
                cancelled = true;
                b.setMessage(Component.literal("Auto-Reconnect abgebrochen"));
                b.active = false;
            }).bounds(w / 2 - 100, h - 34, 200, 20).build();
            Screens.getWidgets(screen).add(btn);
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                if (cancelled || fired[0]) return;
                long left = target[0] - System.currentTimeMillis();
                if (left > 0) {
                    btn.setMessage(Component.literal("Reconnect in " + (left / 1000 + 1) + " s · Klick = abbrechen (" + (tries + 1) + "/" + MAX_TRIES + ")"));
                    return;
                }
                fired[0] = true;
                tries++;
                client.execute(() -> connect(client, new JoinMultiplayerScreen(new TitleScreen()), lastName, lastIp));
            });
        });
    }

    public static void connect(Minecraft mc, Screen from, String name, String ip) {
        if (ip == null || ip.isBlank()) return;
        ServerData info = new ServerData(name == null || name.isBlank() ? "Server" : name, ip.trim(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(from, mc, ServerAddress.parseString(ip.trim()), info, false, null);
    }
}
