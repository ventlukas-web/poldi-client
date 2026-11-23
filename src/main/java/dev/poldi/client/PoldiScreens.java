package dev.poldi.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Inventar-Design (Kisten, Ofen, Crafting, Spieler-Inventar ...) und dunkelroter Hintergrund fuer
 * Server- und Weltauswahl. Beides wird nach dem Vanilla-Hintergrund und vor Slots/Items gezeichnet.
 */
public final class PoldiScreens {
    private PoldiScreens() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> cs && !(screen instanceof CreativeModeInventoryScreen))
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> inventory(cs, g));
            if (screen instanceof JoinMultiplayerScreen || screen instanceof SelectWorldScreen)
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
                    if (!PoldiConfig.get().menuBackground) return;
                    g.fillGradient(0, 0, s.width, s.height,
                        PoldiTheme.alpha(PoldiTheme.lerp(0xFF0A0404, PoldiTheme.PRIMARY, 0.12f), 0xF0),
                        PoldiTheme.alpha(0xFF050202, 0xF0));
                });
        });
    }

    private static int num(Object o, String names) { return (int) PoldiR.num(PoldiR.field(o, names), Integer.MIN_VALUE); }

    private static void inventory(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        if (!PoldiConfig.get().niceInventory) return;
        long p0 = PoldiProf.begin();
        try {
            int x = num(cs, "leftPos"), y = num(cs, "topPos"), w = num(cs, "imageWidth"), h = num(cs, "imageHeight");
            if (x == Integer.MIN_VALUE || w <= 0 || h <= 0) return;
            PoldiTheme.panel(g, x, y, w, h, 0xF0);
            for (Slot s : cs.getMenu().slots) {
                if (!s.isActive()) continue;
                int sx = x + s.x - 1, sy = y + s.y - 1;
                // Slot im Vanilla-Stil: dunkel, oben/links Schatten, unten/rechts Licht
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF1A0C0C);
                g.fill(sx, sy, sx + 17, sy + 1, 0xFF0A0404);
                g.fill(sx, sy, sx + 1, sy + 17, 0xFF0A0404);
                g.fill(sx + 1, sy + 17, sx + 18, sy + 18, PoldiTheme.alpha(PoldiTheme.SECOND, 0x60));
                g.fill(sx + 17, sy + 1, sx + 18, sy + 18, PoldiTheme.alpha(PoldiTheme.SECOND, 0x60));
            }
        } catch (Throwable ignored) {
        } finally {
            PoldiProf.end("Inventar-Design", p0);
        }
    }
}
