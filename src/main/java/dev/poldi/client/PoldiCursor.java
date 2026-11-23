package dev.poldi.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/** Eigener Mauszeiger in allen Menues: Der System-Cursor wird versteckt, stattdessen zeichnet der Client eine Form im Poldi-Design. */
public final class PoldiCursor {
    public static final String[] STYLES = {"Standard", "Pfeil", "Kreuz", "Ring", "Punkt", "Logo", "Raute", "Stern", "Herz", "Fadenkreuz", "Regenbogen"};
    public static final float[] SIZES = {1f, 1.5f, 2f, 3f, 0.75f};

    private PoldiCursor() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> draw(ctx, client, mx, my)));
    }

    private static boolean hidden;

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int mx, int my) {
        PoldiConfig cfg = PoldiConfig.get();
        long win = mc.getWindow().handle();
        int style = Math.floorMod(cfg.cursorStyle, STYLES.length);
        if (style == 0) {
            if (hidden) { GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL); hidden = false; }
            return;
        }
        if (GLFW.glfwGetInputMode(win, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_HIDDEN) GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        hidden = true;

        float sc = SIZES[Math.floorMod(cfg.cursorSize, SIZES.length)];
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(mx, my);
        m.scale(sc, sc);
        int c1 = PoldiTheme.PRIMARY, c2 = PoldiTheme.SECOND, shadow = 0xC0000000;
        switch (style) {
            case 1 -> { // Pfeil: Spitze links oben
                for (int r = 0; r < 14; r++) {
                    int wd = r < 10 ? r + 1 : 3, x0 = r < 10 ? 0 : 3;
                    ctx.fill(x0 - 1, r - 1, x0 + wd + 1, r + 2, shadow);
                }
                for (int r = 0; r < 14; r++) {
                    int wd = r < 10 ? r + 1 : 3, x0 = r < 10 ? 0 : 3;
                    ctx.fill(x0, r, x0 + wd, r + 1, PoldiTheme.lerp(c1, c2, r / 13f));
                }
            }
            case 2 -> { // Kreuz
                int g = 3, l = 6;
                int[][] arms = {{-g - l, -1, -g, 1}, {g, -1, g + l, 1}, {-1, -g - l, 1, -g}, {-1, g, 1, g + l}};
                for (int[] a : arms) ctx.fill(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, shadow);
                for (int i = 0; i < 4; i++) { int[] a = arms[i]; ctx.fill(a[0], a[1], a[2], a[3], PoldiTheme.lerp(c1, c2, i / 3f)); }
            }
            case 3 -> { // Ring mit Punkt (nur Linien, innen frei)
                PoldiTheme.roundOutline(ctx, -8, -8, 16, 16, 8, shadow);
                PoldiTheme.roundOutline(ctx, -6, -6, 12, 12, 6, shadow);
                PoldiTheme.roundOutline(ctx, -7, -7, 14, 14, 7, PoldiTheme.lerp(c1, c2, 0.5f));
                ctx.fill(-2, -2, 2, 2, shadow);
                ctx.fill(-1, -1, 1, 1, 0xFFFFFFFF);
            }
            case 4 -> { // Punkt
                PoldiTheme.roundRect(ctx, -4, -4, 8, 8, 4, shadow);
                PoldiTheme.roundRect(ctx, -3, -3, 6, 6, 3, PoldiTheme.lerp(c1, c2, 0.5f));
                ctx.fill(-1, -2, 1, -1, 0xCCFFFFFF);
            }
            case 6 -> { // Raute
                for (int pass = 0; pass < 2; pass++)
                    for (int r = -7; r <= 7; r++) {
                        int half = 7 - Math.abs(r), e = pass == 0 ? 1 : 0;
                        ctx.fill(-half - e, r - e, half + 1 + e, r + 1 + e, pass == 0 ? shadow : PoldiTheme.lerp(c1, c2, (r + 7) / 14f));
                    }
            }
            case 7 -> { // Stern (vierzackig)
                for (int pass = 0; pass < 2; pass++)
                    for (int r = -9; r <= 9; r++) {
                        int half = (int) Math.round(9 * Math.pow(1 - Math.abs(r) / 10.0, 3)), e = pass == 0 ? 1 : 0;
                        ctx.fill(-half - e, r - e, half + 1 + e, r + 1 + e, pass == 0 ? shadow : PoldiTheme.lerp(c1, c2, (r + 9) / 18f));
                    }
            }
            case 8 -> { // Herz (Pixel)
                String[] h = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};
                for (int pass = 0; pass < 2; pass++)
                    for (int r = 0; r < h.length; r++)
                        for (int cx = 0; cx < 7; cx++) {
                            if (h[r].charAt(cx) != 'X') continue;
                            int e = pass == 0 ? 1 : 0;
                            ctx.fill(cx * 2 - 7 - e, r * 2 - 6 - e, cx * 2 - 5 + e, r * 2 - 4 + e,
                                pass == 0 ? shadow : PoldiTheme.lerp(0xFFFF5C8A, 0xFFB14CFF, r / 5f));
                        }
                ctx.fill(-5, -5, -3, -3, 0xAAFFFFFF);
            }
            case 9 -> { // Fadenkreuz mit Luecke und Mittelpunkt
                int g = 3, l = 6;
                int[][] arms = {{-g - l, 0, -g, 1}, {g, 0, g + l, 1}, {0, -g - l, 1, -g}, {0, g, 1, g + l}};
                for (int[] a : arms) ctx.fill(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, shadow);
                for (int[] a : arms) ctx.fill(a[0], a[1], a[2], a[3], 0xFFFFFFFF);
                ctx.fill(-1, -1, 2, 2, shadow);
                ctx.fill(0, 0, 1, 1, PoldiTheme.lerp(c1, c2, 0.5f));
            }
            case 10 -> { // Regenbogen-Punkt (Farbe wandert)
                int col = 0xFF000000 | java.awt.Color.HSBtoRGB((System.currentTimeMillis() / 12 % 360) / 360f, 0.65f, 1f);
                PoldiTheme.roundRect(ctx, -5, -5, 10, 10, 5, shadow);
                PoldiTheme.roundRect(ctx, -4, -4, 8, 8, 4, col);
                ctx.fill(-2, -3, 0, -2, 0xCCFFFFFF);
            }
            default -> PoldiLogo.draw(ctx, 0, 0, 18); // Logo
        }
        m.popMatrix();
    }
}
