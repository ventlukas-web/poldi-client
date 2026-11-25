package dev.poldi.gametest;

import dev.poldi.client.PoldiMenu;
import dev.poldi.client.PoldiHudEditor;
import dev.poldi.client.PoldiTrailerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Macht echte Ingame-Screenshots vom Poldi Client (Titel, Mod-Menue-Seiten, HUD-Editor, Trailer, Ingame-HUD). */
public class PoldiShots implements FabricClientGameTest {
    private static final int[] PAGES = {0, 3, 18, 16, 22, 23, 4, 2, 11, 12, 13, 8, 20, 9, 19, 14, 6, 7, 10, 17, 21, 5, 15};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.waitTicks(40);
        shot(ctx, "00-titelbildschirm");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(40);
            shot(ctx, "01-ingame-hud");
            int i = 2;
            for (int p : PAGES) {
                final int page = p;
                try {
                    ctx.runOnClient(c -> PoldiMenu.openTab(page));
                    ctx.setScreen(() -> new PoldiMenu(null));
                    ctx.waitTicks(15);
                    shot(ctx, String.format("%02d-menu-seite-%02d", i++, page));
                } catch (Throwable t) { System.err.println("Seite " + page + ": " + t); }
            }
            try { ctx.setScreen(() -> new PoldiHudEditor(null)); ctx.waitTicks(15); shot(ctx, String.format("%02d-hud-editor", i++)); } catch (Throwable t) { System.err.println("HUD-Editor: " + t); }
            try {
                ctx.setScreen(() -> new PoldiTrailerScreen(null));
                for (int k = 0; k < 4; k++) { ctx.waitTicks(60); shot(ctx, String.format("%02d-trailer-%d", i++, k + 1)); }
            } catch (Throwable t) { System.err.println("Trailer: " + t); }
            ctx.setScreen(() -> null);
        }
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        try { ctx.takeScreenshot(name); } catch (Throwable t) { System.err.println("Screenshot " + name + ": " + t); }
    }
}
