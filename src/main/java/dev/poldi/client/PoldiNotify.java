package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Kleine Benachrichtigungen unten rechts im Spiel. */
public final class PoldiNotify {
    private record T(String title, String msg, long at, int color) {}
    private static final List<T> LIST = new ArrayList<>();
    private static final long LIFE = 3800;

    private PoldiNotify() {}

    public static synchronized void push(String title, String msg, int color) {
        LIST.add(new T(title, msg, System.currentTimeMillis(), color));
        while (LIST.size() > 5) LIST.remove(0);
    }

    public static synchronized void render(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        long now = System.currentTimeMillis();
        LIST.removeIf(t -> now - t.at() > LIFE);
        int sw = g.guiWidth(), sh = g.guiHeight();
        int y = sh - 40;
        for (int i = LIST.size() - 1; i >= 0; i--) {
            T t = LIST.get(i);
            long el = now - t.at();
            String m = f.plainSubstrByWidth(t.msg(), 200);
            int w = Math.max(f.width(m), f.width(t.title())) + 16, h = 26;
            float in = Math.min(1f, el / 250f), out = el > LIFE - 300 ? (LIFE - el) / 300f : 1f;
            int x = (int) (sw - 6 - w * in * Math.max(0f, out));
            PoldiTheme.panel(g, x, y - h, w, h);
            PoldiTheme.rect(g, x + 1, y - h + 1, 2, h - 2, t.color());
            PoldiTheme.text(g, f, t.title(), x + 8, y - h + 4, t.color());
            PoldiTheme.text(g, f, m, x + 8, y - h + 14, PoldiTheme.TEXT);
            y -= h + 4;
        }
    }
}
