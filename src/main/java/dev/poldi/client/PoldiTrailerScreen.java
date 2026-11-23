package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Poldi-Trailer (25 s, mit Musik): eine Kamera dreht sich durch Slides mit den Features,
 * ein sichtbarer Mauszeiger klickt Schalter an. Gleiche Zeitleiste wie das Trailer-Video.
 * Ueberspringen: Button unten rechts oder ESC.
 */
public class PoldiTrailerScreen extends PoldiScreen {
    private static final float VW = 640, VH = 360, DUR = 25f;
    private static final int SW = 360, SH = 220;
    private static final float R = 560, STEP = (float) Math.toRadians(38);
    private static final float[][] HOLD = {{0, 2.8f}, {3.8f, 7.8f}, {8.8f, 12.4f}, {13.4f, 16.2f}, {17.2f, 19.8f}, {20.8f, 22.8f}, {23.8f, 25}};
    /** {Zeit, Slide, x, y, Klick} - Mauszeiger-Wegpunkte in Slide-Koordinaten */
    private static final float[][] CUR = {
        {0.0f, 0, 120, 80, 0}, {2.6f, 0, 60, 60, 0},
        {4.2f, 1, 100, 70, 0}, {4.9f, 1, -43, -71, 1}, {5.6f, 1, -86, -1, 1}, {6.3f, 1, 86, 23, 1}, {7.1f, 1, 86, 47, 1}, {7.7f, 1, 120, 90, 0},
        {9.4f, 2, 110, 60, 0}, {10.2f, 2, 134, -46, 1}, {11.4f, 2, 134, 2, 1}, {12.3f, 2, 140, 80, 0},
        {13.8f, 3, 140, 70, 0}, {15.0f, 3, -40, 40, 0}, {16.1f, 3, 40, 70, 0},
        {17.6f, 4, 120, 60, 0}, {18.4f, 4, 71, -71, 1}, {19.2f, 4, 71, -47, 1}, {19.8f, 4, 130, 80, 0},
        {21.2f, 5, 130, 70, 0}, {22.6f, 5, 80, 60, 0},
        {24.2f, 6, 0, 52, 0}, {25, 6, 0, 52, 0}};

    private static SoundInstance music;
    private final Screen parent;
    private long start;
    private boolean closed;
    private GuiGraphicsExtractor g;
    private Font f;

    public PoldiTrailerScreen(Screen parent) {
        super(Component.literal("Poldi Trailer"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        button(() -> "Überspringen »", this::onClose, width - 106, height - 28, 98, 20);
        if (start != 0) return;
        start = System.currentTimeMillis();
        try {
            stopMusic();
            music = new SimpleSoundInstance(PoldiClient.id("trailer"), SoundSource.MASTER, 1.0f, 1.0f, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            Minecraft.getInstance().getSoundManager().play(music);
        } catch (Throwable ignored) {}
    }

    private static void stopMusic() {
        try { if (music != null) Minecraft.getInstance().getSoundManager().stop(music); } catch (Throwable ignored) {}
        music = null;
    }

    @Override
    public void removed() { stopMusic(); }

    @Override
    public void onClose() {
        if (closed) return;
        closed = true;
        stopMusic();
        PoldiConfig c = PoldiConfig.get();
        if (!c.introSeen) { c.introSeen = true; c.save(); }
        open(parent);
    }

    // ---------- Mathe ----------

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static float ease(float t) { t = clamp(t); return t < .5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }
    private static float out3(float t) { t = clamp(t); return 1 - (float) Math.pow(1 - t, 3); }

    private static float camIndex(float t) {
        for (int i = 0; i < HOLD.length; i++) {
            if (t <= HOLD[i][1]) return i;
            if (i + 1 < HOLD.length && t < HOLD[i + 1][0]) return i + ease((t - HOLD[i][1]) / (HOLD[i + 1][0] - HOLD[i][1]));
        }
        return HOLD.length - 1;
    }

    private static float camZoom(float t) {
        float ci = camIndex(t), fr = ci - (float) Math.floor(ci);
        float z = 1 - .48f * (float) Math.sin(Math.PI * fr);
        for (float[] h : HOLD) if (t >= h[0] && t <= h[1]) z *= 1 + .035f * ((t - h[0]) / (h[1] - h[0] + .001f));
        if (t < 1.2f) z *= .82f + .18f * out3(t / 1.2f);
        return z;
    }

    private record Cam(float cx, float cy, float rot, float z) {}

    private static float[] slidePos(int i) { float a = i * STEP; return new float[]{R * (float) Math.sin(a), R - R * (float) Math.cos(a), a}; }

    private static Cam camera(float t) {
        float a = camIndex(t) * STEP;
        return new Cam(R * (float) Math.sin(a), R - R * (float) Math.cos(a), -a + (float) Math.sin(t * 1.3) * .012f, camZoom(t));
    }

    private static float[] w2s(Cam c, float x, float y) {
        float dx = x - c.cx(), dy = y - c.cy(), co = (float) Math.cos(c.rot()), si = (float) Math.sin(c.rot());
        return new float[]{VW / 2 + (dx * co - dy * si) * c.z(), VH / 2 + (dx * si + dy * co) * c.z()};
    }

    private static float[] s2w(int i, float x, float y) {
        float[] p = slidePos(i);
        float co = (float) Math.cos(p[2]), si = (float) Math.sin(p[2]);
        return new float[]{p[0] + x * co - y * si, p[1] + x * si + y * co};
    }

    private static float[] cursorAt(float t, Cam cam) {
        int k = 0;
        while (k + 1 < CUR.length && CUR[k + 1][0] <= t) k++;
        float[] a = CUR[k], b = CUR[Math.min(CUR.length - 1, k + 1)];
        float[] wa = s2w((int) a[1], a[2], a[3]), wb = s2w((int) b[1], b[2], b[3]);
        float[] pa = w2s(cam, wa[0], wa[1]), pb = w2s(cam, wb[0], wb[1]);
        float u = b[0] > a[0] ? ease((t - a[0]) / (b[0] - a[0])) : 0;
        return new float[]{pa[0] + (pb[0] - pa[0]) * u, pa[1] + (pb[1] - pa[1]) * u};
    }

    // ---------- Zeichnen ----------

    private static int argb(int rgb, float a) { return ((int) (clamp(a) * 255) << 24) | (rgb & 0xFFFFFF); }

    private void rect(float x, float y, float w, float h, int rgb, float a) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), argb(rgb, a));
    }

    private void rect(float x, float y, float w, float h, int rgb) { rect(x, y, w, h, rgb, 1); }

    private void outline(float x, float y, float w, float h, int rgb, float a) {
        rect(x, y, w, 1, rgb, a); rect(x, y + h - 1, w, 1, rgb, a); rect(x, y, 1, h, rgb, a); rect(x + w - 1, y, 1, h, rgb, a);
    }

    private void bevel(float x, float y, float w, float h, int base, boolean hover) {
        rect(x, y, w, h, hover ? 0xFFFFFF : 0x000000);
        rect(x + 1, y + 1, w - 2, h - 2, base);
        rect(x + 1, y + 1, w - 2, 1, PoldiTheme.shade(base | 0xFF000000, 1.35f));
        rect(x + 1, y + 2, 1, h - 4, PoldiTheme.shade(base | 0xFF000000, 1.2f));
        rect(x + 1, y + h - 3, w - 2, 2, PoldiTheme.shade(base | 0xFF000000, .62f));
        rect(x + w - 2, y + 2, 1, h - 5, PoldiTheme.shade(base | 0xFF000000, .75f));
    }

    private void panel(float x, float y, float w, float h, float a) {
        rect(x, y, w, h, 0x140606, .88f * a);
        outline(x, y, w, h, 0x000000, a);
        outline(x + 1, y + 1, w - 2, h - 2, PoldiTheme.PRIMARY, .56f * a);
    }

    private void panel(float x, float y, float w, float h) { panel(x, y, w, h, 1); }

    /** align: 0 links, 1 Mitte, 2 rechts. size = Hoehe in virtuellen Pixeln. */
    private void text(String s, float x, float y, int rgb, float size, float a, boolean bold, int align) {
        if (a <= 0.02f) return;
        Component c = bold ? PoldiTheme.title(s) : PoldiTheme.ui(s);
        float sc = size / 9f;
        float w = f.width(c) * sc;
        float px = align == 1 ? x - w / 2 : align == 2 ? x - w : x;
        g.pose().pushMatrix();
        g.pose().translate(px, y);
        g.pose().scale(sc, sc);
        g.text(f, c, 0, 0, argb(rgb, a), true);
        g.pose().popMatrix();
    }

    private void text(String s, float x, float y, int rgb, float size) { text(s, x, y, rgb, size, 1, false, 0); }

    private int btnBase() { return PoldiButton.baseColor(false) & 0xFFFFFF; }
    private int accBase() { return PoldiButton.baseColor(true) & 0xFFFFFF; }

    private void button(String label, float x, float y, float w, float h, boolean accent, boolean hover) {
        int base = accent ? accBase() : btnBase();
        if (hover) base = PoldiTheme.lerp(base | 0xFF000000, PoldiTheme.PRIMARY, .45f) & 0xFFFFFF;
        bevel(x, y, w, h, base, hover);
        text(label, x + w / 2, y + (h - 8) / 2 - 1, 0xFFFFFF, 8, 1, false, 1);
    }

    private void logo(float x, float y, int s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        PoldiLogo.draw(g, 0, 0, s);
        g.pose().popMatrix();
    }

    private void slideFrame(String title, String sub) {
        panel(-SW / 2f, -SH / 2f, SW, SH);
        logo(-SW / 2f + 10, -SH / 2f + 8, 14);
        text(title, -SW / 2f + 30, -SH / 2f + 10, PoldiTheme.PRIMARY, 10, 1, true, 0);
        if (sub != null) text(sub, SW / 2f - 10, -SH / 2f + 12, 0xC9B4B4, 7, 1, false, 2);
    }

    private void toggleRow(String label, boolean on, float x, float y, float w) { button(label + ": " + (on ? "AN" : "AUS"), x, y, w, 18, on, false); }

    private void slide(int i, float t) {
        int P = PoldiTheme.PRIMARY & 0xFFFFFF, MUT = 0xC9B4B4;
        switch (i) {
            case 0 -> {
                logo(-34, -88, 68);
                text("POLDI", 0, -10, 0xFFFFFF, 34, 1, true, 1);
                text("CLIENT", 0, 28, P, 22, 1, true, 1);
                text("Minecraft 26.2 · Fabric", 0, 62, MUT, 9, 1, false, 1);
            }
            case 1 -> {
                slideFrame("Mod-Menü", "Right Shift");
                String[] cats = {"General", "Survival", "Visuals", "PvP", "Streaming", "Tools", "Commands"};
                int sel = t >= 4.9f ? 1 : 0;
                for (int k = 0; k < cats.length; k++) button(cats[k], -SW / 2f + 12 + (k % 4) * 84, -SH / 2f + 30 + (k / 4) * 19, 82, 17, k == sel, false);
                outline(-SW / 2f + 11 + (sel % 4) * 84, -SH / 2f + 29 + (sel / 4) * 19, 84, 19, 0xFFFFFF, 1);
                button(sel == 1 ? "Survival · Warnungen · 1/2" : "Allgemein · 1/4", -SW / 2f + 56, -SH / 2f + 72, SW - 112, 18, true, false);
                button("<", -SW / 2f + 12, -SH / 2f + 72, 40, 18, false, false);
                button(">", SW / 2f - 52, -SH / 2f + 72, 40, 18, false, false);
                String[] names = sel == 1 ? new String[]{"Todespunkt merken", "Pickup-Log", "Werkzeug-Warnung", "Licht-Overlay", "Hunger-Warnung", "Tag + Schlafen"}
                    : new String[]{"Keystrokes", "Koordinaten", "Toasts", "Wasserzeichen", "Sound", "Intro-Trailer"};
                float[] at = sel == 1 ? new float[]{5.6f, -1, -1, 6.3f, -1, 7.1f} : new float[]{-1, -1, -1, -1, -1, -1};
                for (int k = 0; k < 6; k++) toggleRow(names[k], at[k] < 0 || t >= at[k], -SW / 2f + 12 + (k % 2) * 172, -SH / 2f + 100 + (k / 2) * 24, 164);
                button("Tasten …", -SW / 2f + 12, SH / 2f - 30, 104, 18, true, false);
                button("Schließen", -52, SH / 2f - 30, 104, 18, false, false);
                button("HUD verschieben", SW / 2f - 116, SH / 2f - 30, 104, 18, true, false);
            }
            case 2 -> {
                slideFrame("Survival", "Neue Features");
                String[][] feats = {{"Todespunkt", "Wegpunkt \"Tod\" + Pfeil"}, {"Hunger-Warnung", "Toast bei wenig Essen"}, {"Werkzeug-Warnung", "bevor es kaputt geht"},
                    {"Inventar voll", "Warnung + Ton"}, {"Licht-Overlay", "zeigt Mob-Spawns"}, {"Tag + Schlafen", "Tag-Zähler & Bett-Zeit"}};
                for (int k = 0; k < feats.length; k++) {
                    float y = -SH / 2f + 30 + k * 24;
                    rect(-SW / 2f + 12, y, SW - 24, 20, 0xFFFFFF, .04f);
                    outline(-SW / 2f + 12, y, SW - 24, 20, 0x2C2C2C, 1);
                    text(feats[k][0], -SW / 2f + 20, y + 3, 0xFFFFFF, 8);
                    text(feats[k][1], -SW / 2f + 20, y + 12, MUT, 6);
                    boolean on = k == 1 ? t >= 10.2f : k == 3 ? t >= 11.4f : true;
                    button(on ? "AN" : "AUS", SW / 2f - 76, y + 2, 60, 16, on, false);
                }
                float ta = clamp((t - 10.4f) / .3f) * (1 - clamp((t - 12.0f) / .3f));
                if (ta > 0) {
                    float x = SW / 2f - 140 + (1 - out3((t - 10.4f) / .3f)) * 60, y = SH / 2f - 38;
                    panel(x, y, 128, 28, ta);
                    rect(x + 1, y + 1, 2, 26, 0xFBBF24, ta);
                    text("Hunger niedrig", x + 8, y + 4, 0xFBBF24, 8, ta, false, 0);
                    text("Nur noch 6/20 – iss etwas!", x + 8, y + 15, 0xFFFFFF, 6.5f, ta, false, 0);
                }
            }
            case 3 -> {
                slideFrame("HUD", "alles verschiebbar");
                panel(-SW / 2f + 14, -SH / 2f + 30, 150, 74);
                String[][] rows = {{"XYZ", "128 64 -310"}, {"Nether", "16 -38"}, {"Blick", "Norden (-Z)"}, {"Biom", "Plains"},
                    {"FPS", String.valueOf(140 + Math.round(Math.sin(t * 3) * 9))}};
                for (int k = 0; k < rows.length; k++) {
                    text(rows[k][0], -SW / 2f + 20, -SH / 2f + 36 + k * 13, MUT, 8);
                    text(rows[k][1], -SW / 2f + 158, -SH / 2f + 36 + k * 13, 0xFFFFFF, 8, 1, false, 2);
                }
                float kx = -SW / 2f + 20, ky = -SH / 2f + 116;
                int s = 22, gp = 2, ph = (int) Math.floor(t * 3) % 4;
                key(kx + s + gp, ky, s, s, "W", ph <= 1);
                key(kx, ky + s + gp, s, s, "A", ph == 2);
                key(kx + s + gp, ky + s + gp, s, s, "S", false);
                key(kx + (s + gp) * 2, ky + s + gp, s, s, "D", ph == 3);
                key(kx, ky + (s + gp) * 2, 34, 14, "LMB 9", ph % 2 == 0);
                key(kx + 36, ky + (s + gp) * 2, 34, 14, "RMB 0", false);
                String[] mods = {"Licht-Overlay", "Koordinaten", "Keystrokes", "Tag-Zähler", "Fullbright"};
                for (int k = 0; k < mods.length; k++) {
                    float w = f.width(PoldiTheme.ui(mods[k])) * 8 / 9f + 10, x = SW / 2f - 14 - w, y = -SH / 2f + 30 + k * 13;
                    int c = PoldiTheme.lerp(PoldiTheme.PRIMARY, PoldiTheme.SECOND, k / 4f) & 0xFFFFFF;
                    rect(x, y, w, 12, 0x140606, .8f);
                    rect(SW / 2f - 16, y, 2, 12, c);
                    text(mods[k], x + 4, y + 2, c, 8);
                }
                panel(SW / 2f - 170, SH / 2f - 62, 156, 46);
                text("Zeit", SW / 2f - 164, SH / 2f - 57, P, 8, 1, true, 0);
                text("Tag 42", SW / 2f - 164, SH / 2f - 44, 0xFFFFFF, 8);
                text("19:40", SW / 2f - 20, SH / 2f - 44, 0xFFFFFF, 8, 1, false, 2);
                text("Schlafen", SW / 2f - 164, SH / 2f - 32, 0x86EFAC, 8);
                text("jetzt möglich", SW / 2f - 20, SH / 2f - 32, 0x86EFAC, 8, 1, false, 2);
            }
            case 4 -> {
                slideFrame("PvP & Tools", "nur Anzeigen");
                panel(-SW / 2f + 14, -SH / 2f + 30, 120, 46);
                text("PvP", -SW / 2f + 20, -SH / 2f + 35, P, 8, 1, true, 0);
                text("CPS", -SW / 2f + 20, -SH / 2f + 48, 0xFFFFFF, 8);
                text((10 + Math.round(Math.abs(Math.sin(t * 2)) * 4)) + " | 3", -SW / 2f + 128, -SH / 2f + 48, 0xFFFFFF, 8, 1, false, 2);
                text("Reach", -SW / 2f + 20, -SH / 2f + 60, 0xFFFFFF, 8);
                text("2.87", -SW / 2f + 128, -SH / 2f + 60, 0xFFFFFF, 8, 1, false, 2);
                String[] list = {"Autoclicker", "Auto-Sprint", "Sell-Makro", "Fullbright"};
                float[] at = {18.4f, 19.2f, -1, -1};
                for (int k = 0; k < list.length; k++) toggleRow(list[k], at[k] < 0 || t >= at[k], -14, -SH / 2f + 30 + k * 24, 170);
                panel(-100, SH / 2f - 60, 200, 27);
                float bear = (t * 40) % 360;
                String[] dirs = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
                for (int b = -105; b <= 105; b += 15) {
                    double abs = Math.floor(bear / 15) * 15 + b, off = abs - bear;
                    if (Math.abs(off) > 90) continue;
                    float px = (float) (off / 90 * 90);
                    int ab = (int) (((Math.round(abs) % 360) + 360) % 360);
                    if (ab % 45 == 0) text(dirs[ab / 45], px, SH / 2f - 47, ab == 0 ? P : 0xFFFFFF, 8, 1, false, 1);
                    else rect(px, SH / 2f - 45, 1, 4, MUT, .6f);
                }
                rect(0, SH / 2f - 38, 1, 4, PoldiTheme.SECOND & 0xFFFFFF);
            }
            case 5 -> {
                slideFrame("Streaming", "Twitch · YouTube");
                panel(-SW / 2f + 14, -SH / 2f + 30, 232, 120);
                text("Twitch", -SW / 2f + 20, -SH / 2f + 35, P, 8, 1, true, 0);
                text("verbunden: poldi", -SW / 2f + 240, -SH / 2f + 35, MUT, 7, 1, false, 2);
                String[][] msgs = {{"Mika", "gg das war knapp"}, {"Lena", "zeig mal die base!"}, {"Tom", "join"},
                    {"Noah", "welcher client ist das?"}, {"Ali", "Poldi!"}, {"Sophie", "join"}};
                int[] cols = {0x7DD3FC, 0xFBBF24, 0x86EFAC, 0xF472B6, 0xFCA5A5, 0xA5B4FC};
                int n = Math.max(0, Math.min(msgs.length, (int) Math.floor((t - 21.0f) / .3f)));
                for (int k = 0; k < n; k++) {
                    float w = f.width(PoldiTheme.ui(msgs[k][0] + ": ")) * 8 / 9f;
                    text(msgs[k][0] + ": ", -SW / 2f + 20, -SH / 2f + 50 + k * 14, cols[k], 8);
                    text(msgs[k][1], -SW / 2f + 20 + w, -SH / 2f + 50 + k * 14, 0xFFFFFF, 8);
                }
                panel(SW / 2f - 104, -SH / 2f + 30, 90, 46);
                text("Events", SW / 2f - 98, -SH / 2f + 35, P, 8, 1, true, 0);
                text("Giveaway", SW / 2f - 98, -SH / 2f + 48, 0xFFFFFF, 7);
                text(Math.max(0, (int) Math.ceil(30 - (t - 20.8f) * 3)) + "s", SW / 2f - 20, -SH / 2f + 48, 0xFFFFFF, 7, 1, false, 2);
                text("Teilnehmer", SW / 2f - 98, -SH / 2f + 60, 0xFFFFFF, 7);
                text(String.valueOf(n >= 6 ? 2 : n >= 3 ? 1 : 0), SW / 2f - 20, -SH / 2f + 60, 0xFFFFFF, 7, 1, false, 2);
                button("Streamer-Modus: AN", SW / 2f - 104, -SH / 2f + 84, 90, 18, true, false);
            }
            default -> {
                logo(-30, -84, 60);
                text("POLDI", 0, -14, 0xFFFFFF, 34, 1, true, 1);
                text("CLIENT", 0, 24, P, 22, 1, true, 1);
                button("Jetzt spielen", -60, 58, 120, 20, true, t > 24.1f);
                text("Right Shift = Mod-Menü", 0, 86, MUT, 8, 1, false, 1);
            }
        }
    }

    private void key(float x, float y, float w, float h, String l, boolean down) {
        bevel(x, y, w, h, down ? PoldiTheme.PRIMARY & 0xFFFFFF : btnBase(), false);
        text(l, x + w / 2, y + (h - 8) / 2 - 1, 0xFFFFFF, 8, 1, false, 1);
    }

    private void background(float t, Cam cam) {
        g.fillGradient(0, 0, (int) VW, (int) VH, 0xFF1A0606, 0xFF060202);
        g.pose().pushMatrix();
        g.pose().translate(VW / 2, VH / 2);
        g.pose().rotate(cam.rot());
        g.pose().scale(cam.z(), cam.z());
        g.pose().translate(-cam.cx(), -cam.cy());
        int line = argb(PoldiTheme.PRIMARY, .07f), gs = 48;
        int x0 = (int) Math.floor((cam.cx() - 900) / gs) * gs, y0 = (int) Math.floor((cam.cy() - 900) / gs) * gs;
        int top = (int) (cam.cy() - 900), bot = (int) (cam.cy() + 900), lft = (int) (cam.cx() - 900), rgt = (int) (cam.cx() + 900);
        for (int x = x0; x < cam.cx() + 900; x += gs) g.fill(x, top, x + 1, bot, line);
        for (int y = y0; y < cam.cy() + 900; y += gs) g.fill(lft, y, rgt, y + 1, line);
        g.pose().popMatrix();
        for (int i = 0; i < 40; i++) {
            float seed = i * 37.17f, px = (float) ((Math.sin(seed) * .5 + .5) * VW), sp = 14 + (i % 7) * 5;
            float py = VH - ((t * sp + (seed % 90) * 4) % (VH + 20));
            float a = .5f * (float) (.4 + .6 * Math.abs(Math.sin(t * 2 + i)));
            rect(px, py, 2, 2, i % 3 != 0 ? 0xFF8A3C : PoldiTheme.SECOND & 0xFFFFFF, a);
        }
    }

    private void cursor(float x, float y, boolean down) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.25f, 1.25f);
        // Pfeil aus Pixel-Zeilen: schwarzer Rand, weisse Fuellung
        for (int r = 0; r < 14; r++) {
            int wd = r < 10 ? r + 1 : 3, x0 = r < 10 ? 0 : 3 + (r - 10);
            g.fill(x0 - 1, r - 1, x0 + wd + 1, r + 2, 0xFF000000);
        }
        for (int r = 0; r < 14; r++) {
            int wd = r < 10 ? r + 1 : 3, x0 = r < 10 ? 0 : 3 + (r - 10);
            g.fill(x0, r, x0 + wd, r + 1, down ? 0xFFFFD0D0 : 0xFFFFFFFF);
        }
        g.pose().popMatrix();
    }

    @Override
    public void tick() {
        super.tick();
        if (start != 0 && System.currentTimeMillis() - start >= DUR * 1000) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gg, int mouseX, int mouseY, float delta) {
        gg.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float delta) {
        this.g = gg;
        this.f = font;
        float t = (System.currentTimeMillis() - start) / 1000f;
        t = Math.min(t, DUR - 0.001f);
        float sc = Math.min(width / VW, height / VH);
        gg.pose().pushMatrix();
        gg.pose().translate((width - VW * sc) / 2f, (height - VH * sc) / 2f);
        gg.pose().scale(sc, sc);
        Cam cam = camera(t);
        background(t, cam);
        for (int i = 0; i < HOLD.length; i++) {
            float[] sp = slidePos(i), p = w2s(cam, sp[0], sp[1]);
            if (Math.abs(p[0] - VW / 2) > VW * 1.2f || Math.abs(p[1] - VH / 2) > VH * 1.4f) continue;
            gg.pose().pushMatrix();
            gg.pose().translate(p[0], p[1]);
            gg.pose().rotate(cam.rot() + sp[2]);
            gg.pose().scale(cam.z(), cam.z());
            slide(i, t);
            gg.pose().popMatrix();
        }
        float[] m = cursorAt(t, cam);
        boolean down = false;
        for (float[] c : CUR) {
            if (c[4] == 0 || t < c[0] || t >= c[0] + .45f) continue;
            float k = (t - c[0]) / .45f;
            if (k < .3f) down = true;
            float r = 3 + 12 * k;
            outline(m[0] - r, m[1] - r, r * 2, r * 2, 0xFFFFFF, 1 - k);
        }
        cursor(m[0], m[1], down);
        float fade = Math.max(1 - clamp(t / .5f), clamp((t - (DUR - .6f)) / .6f));
        if (fade > 0) rect(0, 0, VW, VH, 0x000000, fade);
        gg.pose().popMatrix();
        // Skip-Button obendrauf
        for (PoldiButton b : btns) b.extractRenderState(gg, mouseX, mouseY, delta);
    }
}
