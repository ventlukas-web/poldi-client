package dev.poldi.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;

/** Hauptmenue: grosser Beitreten-Button, Mod-Menue-Knopf unten rechts, Branding, Intro-Trailer beim Start. */
public final class PoldiTitle {
    private static boolean introDone = false;

    private PoldiTitle() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen)) return;
            PoldiConfig cfg = PoldiConfig.get();
            if (!introDone && cfg.introTrailer) {
                introDone = true;
                client.execute(() -> client.gui.setScreen(new PoldiTrailerScreen(screen)));
                return;
            }
            introDone = true;
            // Grosser Beitreten-Button ueber "Einzelspieler"
            if (cfg.joinButton) {
                String ip = cfg.serverIp == null ? "" : cfg.serverIp.trim();
                String name = cfg.serverName == null || cfg.serverName.isBlank() ? ip : cfg.serverName.trim();
                PoldiButton join = new PoldiButton(w / 2 - 100, h / 4 + 48 - 24, 200, 20,
                    () -> ip.isEmpty() ? "Server festlegen …" : "Beitreten: " + name, () -> {
                        if (ip.isEmpty()) { PoldiMenu.openTab(18); client.gui.setScreen(new PoldiMenu(screen)); }
                        else PoldiReconnect.connect(client, screen, name, ip);
                    }, true);
                Screens.getWidgets(screen).add(join);
            }
            // Mod-Menue-Knopf unten rechts
            Screens.getWidgets(screen).add(new PoldiButton(w - 96, h - 36, 88, 20, () -> "Mod-Menü",
                () -> client.gui.setScreen(new PoldiMenu(screen)), false));
        });
    }
}
