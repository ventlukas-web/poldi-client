package dev.poldi.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

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
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                Font f = client.font;
                Component t = PoldiTheme.title("POLDI CLIENT");
                float sc = 1.4f;
                int tw = (int) (f.width(t) * sc);
                int x = (s.width - tw) / 2 + 8, y = s.height - 30;
                PoldiLogo.draw(g, x - 20, y - 2, 16);
                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale(sc, sc);
                g.text(f, t, 0, 0, PoldiTheme.PRIMARY, true);
                g.pose().popMatrix();
            });
        });
    }
}
