package dev.poldi.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Rotes Vanilla-Design: eckige Flaechen mit Bevel-Kanten wie die Minecraft-Buttons, leicht rot getoent. */
public final class PoldiTheme {
    /** Hauptfarbe (Standard Rot) und Zweitfarbe (Standard Hellrot), einstellbar im Design. */
    public static int PRIMARY = 0xFFDC2626;
    public static int SECOND = 0xFFF87171;
    public static int BG = 0xD0140606;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFC9B4B4;
    public static final int GOOD = 0xFF86EFAC;
    public static final int BAD = 0xFFFDA4AF;
    public static final int WARN = 0xFFFBBF24;

    private PoldiTheme() {}

    /** "#RRGGBB" (mit oder ohne #) -> ARGB, sonst null. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(s, 16);
    }

    public static String toHex(int argb) { return String.format("#%06X", argb & 0xFFFFFF); }

    public static void reload(PoldiConfig c) {
        Integer a = parseHex(c.color1), b = parseHex(c.color2);
        if (a != null) PRIMARY = a;
        if (b != null) SECOND = b;
        int op = Math.max(32, Math.min(255, c.bgOpacity));
        Integer base = parseHex(c.themeBase);
        BG = alpha(lerp(base != null ? base : 0xFF0C0505, PRIMARY, 0.08f), op);
    }

    public static int bgA(int a) { return alpha(BG, ((BG >>> 24) * a) / 255); }

    public static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 0xFF, cb = (b >> s) & 0xFF;
            r |= ((int) (ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }

    public static int alpha(int color, int a) { return (color & 0xFFFFFF) | ((Math.max(0, Math.min(255, a))) << 24); }

    public static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 0xFF) * f)), g = Math.min(255, (int) (((c >> 8) & 0xFF) * f)), b = Math.min(255, (int) ((c & 0xFF) * f));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ---------- Formen (eckig, Vanilla-Stil) ----------

    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, c);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    /** Nur die Ecken minimal abgeschnitten (1 px) - wirkt "clean", bleibt aber Vanilla-eckig. */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (w <= 2 || h <= 2) { rect(g, x, y, w, h, c); return; }
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (w <= 2 || h <= 2) { outline(g, x, y, w, h, c); return; }
        g.fill(x + 1, y, x + w - 1, y + 1, c);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    /** Button im Vanilla-Look: schwarzer Rand, helle Kante oben/links, dunkle Kante unten/rechts. */
    public static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int base, boolean hover, boolean active) {
        int fill = active ? base : lerp(base, 0xFF2A2020, 0.6f);
        g.fill(x, y, x + w, y + h, hover ? 0xFFFFFFFF : 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, shade(fill, 1.35f));
        g.fill(x + 1, y + 2, x + 2, y + h - 2, shade(fill, 1.2f));
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, shade(fill, 0.62f));
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, shade(fill, 0.75f));
    }

    /** Panel: dunkel, leicht durchsichtig, schwarzer Rand mit roter Innenlinie. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int bgAlpha) {
        g.fill(x, y, x + w, y + h, alpha(BG, bgAlpha));
        outline(g, x, y, w, h, 0xFF000000);
        outline(g, x + 1, y + 1, w - 2, h - 2, alpha(PRIMARY, 0x90));
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) { panel(g, x, y, w, h, (BG >>> 24)); }

    public static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i += 3) g.fill(x + i, y, x + Math.min(w, i + 3), y + h, lerp(c1, c2, w <= 1 ? 0 : (float) i / (w - 1)));
    }

    // ---------- Text ----------

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) { g.text(f, s, x, y, color, true); }

    public static void text(GuiGraphicsExtractor g, Font f, Component s, int x, int y, int color) { g.text(f, s, x, y, color, true); }

    /** Titeltext in Hauptfarbe mit leichtem Verlauf zur Zweitfarbe. Rueckgabe = Breite. */
    public static int gradientText(GuiGraphicsExtractor g, Font f, String s, int x, int y) {
        int cx = x, n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.text(f, ch, cx, y, lerp(PRIMARY, SECOND, (float) i / n * 0.6f), true);
            cx += f.width(ch);
        }
        return cx - x;
    }

    /** Menue-Text: normale Minecraft-Schrift (Vanilla). */
    public static Component ui(String s) { return Component.literal(s); }

    /** Titel-Text: Minecraft-Schrift, fett. */
    public static Component title(String s) { return Component.literal(s).setStyle(Style.EMPTY.withBold(true)); }
}
