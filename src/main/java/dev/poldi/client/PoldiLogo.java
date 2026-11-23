package dev.poldi.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Poldi-Logo aus assets/poldi/textures/gui/logo.png (quadratisch, beliebige Groesse). */
public final class PoldiLogo {
    public static final Identifier TEXTURE = PoldiClient.id("textures/gui/logo.png");
    private static final int SRC = 64;

    private PoldiLogo() {}

    public static void draw(GuiGraphicsExtractor g, int x, int y, int s) {
        if (s < 4) return;
        // ganze Textur auf s x s skalieren
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, s, s, SRC, SRC, SRC, SRC);
    }
}
