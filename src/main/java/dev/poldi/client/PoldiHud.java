package dev.poldi.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class PoldiHud {
    /** Zuletzt gezeichnete Position/Groesse je HUD-Element: {x, y, w, h} (fuer den HUD-Editor). */
    public static final java.util.Map<String, float[]> BOUNDS = new java.util.LinkedHashMap<>();

    /** Position eines Elements: gespeicherte (verschobene) Position oder Standard. */
    static float[] place(String id, float defX, float defY, float bw, float bh, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float[] f = PoldiConfig.get().hudPos.get(id);
        float x = (f != null && f.length >= 2) ? f[0] * sw : defX;
        float y = (f != null && f.length >= 2) ? f[1] * sh : defY;
        x = Math.max(0, Math.min(sw - bw, x));
        y = Math.max(0, Math.min(sh - bh, y));
        BOUNDS.put(id, new float[]{x, y, bw, bh});
        return new float[]{x, y};
    }

    private static long lastNano = System.nanoTime();

    public static void render(GuiGraphicsExtractor g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        float[] sbb = BOUNDS.get("scoreboard");
        BOUNDS.clear();
        if (sbb != null) BOUNDS.put("scoreboard", sbb);
        if (mc.gui.hud.isHidden()) return;
        PoldiConfig cfg = PoldiConfig.get();
        Font f = mc.font;
        long nowN = System.nanoTime();
        float dt = Math.min(0.1f, (nowN - lastNano) / 1e9f);
        lastNano = nowN;

        PoldiProf.frame();
        long p0 = PoldiProf.begin();
        renderInfo(g, f, mc, cfg, 6, dt);
        renderKeys(g, f, mc, cfg);
        renderModList(g, f, mc, cfg);
        PoldiProf.end("HUD Grundpanels", p0);
        p0 = PoldiProf.begin();
        PoldiPanels.render(g, f, mc, cfg, tick.getGameTimeDeltaPartialTick(true));
        PoldiProf.end("HUD Zusatz-Panels", p0);
        if (PoldiEvents.swActive()) {
            String sw = "Stoppuhr " + PoldiEvents.swFmt(PoldiEvents.swElapsed());
            PoldiTheme.text(g, f, sw, (g.guiWidth() - f.width(sw)) / 2, 22, PoldiEvents.swElapsed() > 0 ? PoldiTheme.SECOND : PoldiTheme.TEXT);
        }
        int ty = PoldiEvents.swActive() ? 34 : 22;
        for (String tl : PoldiEvents.timerLines()) {
            PoldiTheme.text(g, f, tl, (g.guiWidth() - f.width(tl)) / 2, ty, PoldiTheme.PRIMARY);
            ty += 11;
        }
    }

    // ---------- Info-Panel ----------

    private static double lastX, lastZ, speedSmooth;
    private static boolean haveLast;

    private static String dirName(Direction d) {
        return switch (d) {
            case NORTH -> "Norden (-Z)";
            case SOUTH -> "Süden (+Z)";
            case EAST -> "Osten (+X)";
            case WEST -> "Westen (-X)";
            default -> d.getName();
        };
    }

    private static void renderInfo(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg, float y0, float dt) {
        var p = mc.player;
        if (p == null) { haveLast = false; return; }
        if (haveLast && dt > 0) {
            double d = Math.hypot(p.getX() - lastX, p.getZ() - lastZ) / dt;
            speedSmooth += (d - speedSmooth) * Math.min(1.0, dt * 6);
        }
        lastX = p.getX(); lastZ = p.getZ(); haveLast = true;

        boolean hide = cfg.streamer && cfg.streamerHideXyz;
        List<String[]> rows = new ArrayList<>();
        if (cfg.coords) {
            rows.add(new String[]{"XYZ", hide ? "versteckt" : (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ())});
            if (cfg.coordsNether && !hide) { String o = PoldiSurvival.otherDim(mc); if (o != null) rows.add(new String[]{o.substring(0, o.indexOf(' ')), o.substring(o.indexOf(' ') + 1)}); }
            rows.add(new String[]{"Blick", dirName(p.getDirection())});
            if (cfg.coordsBiome) rows.add(new String[]{"Biom", PoldiSurvival.biome(mc)});
        }
        if (cfg.showFps) rows.add(new String[]{"FPS", String.valueOf(mc.getFps())});
        if (cfg.ping && mc.getConnection() != null) {
            var e = mc.getConnection().getPlayerInfo(p.getUUID());
            rows.add(new String[]{"Ping", e == null ? "?" : e.getLatency() + " ms"});
        }
        if (cfg.speed) rows.add(new String[]{"Speed", String.format(java.util.Locale.ROOT, "%.1f b/s", speedSmooth)});
        if (cfg.clock) rows.add(new String[]{"Uhr", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))});
        if (rows.isEmpty()) return;

        int w = 0;
        for (String[] r : rows) w = Math.max(w, f.width(r[0]) + 14 + f.width(r[1]));
        int pad = 6, h = rows.size() * 11 + pad * 2 - 1;
        float[] pos = place("info", 6, y0, (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        PoldiTheme.panel(g, 0, 0, w + pad * 2, h);
        int y = pad + 1;
        for (String[] r : rows) {
            PoldiTheme.text(g, f, r[0], pad, y, PoldiTheme.MUTED);
            PoldiTheme.text(g, f, r[1], w + pad - f.width(r[1]), y, PoldiTheme.TEXT);
            y += 11;
        }
        g.pose().popMatrix();
    }

    // ---------- Keystrokes + CPS ----------

    private static final java.util.ArrayDeque<Long> leftClicks = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Long> rightClicks = new java.util.ArrayDeque<>();
    private static boolean wasLeft, wasRight;

    private static int cps(java.util.ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static void key(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, String label, boolean down) {
        PoldiTheme.bevel(g, x, y, w, h, down ? PoldiTheme.PRIMARY : PoldiTheme.alpha(PoldiButton.baseColor(false), 0xC0), false, true);
        PoldiTheme.text(g, f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private static void renderKeys(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        long now = System.currentTimeMillis();
        var o = mc.options;
        boolean l = o.keyAttack.isDown(), r = o.keyUse.isDown();
        if (l && !wasLeft) leftClicks.addLast(now);
        if (r && !wasRight) rightClicks.addLast(now);
        wasLeft = l; wasRight = r;
        if (!cfg.keystrokes) return;

        int s = 22, gp = 2;
        int totalW = s * 3 + gp * 2, totalH = s * 2 + gp * 2 + 16 + gp + 14;
        float[] pos = place("keys", 8, g.guiHeight() - 8 - totalH * cfg.hudScale, totalW * cfg.hudScale, totalH * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        key(g, f, s + gp, 0, s, s, "W", o.keyUp.isDown());
        key(g, f, 0, s + gp, s, s, "A", o.keyLeft.isDown());
        key(g, f, s + gp, s + gp, s, s, "S", o.keyDown.isDown());
        key(g, f, (s + gp) * 2, s + gp, s, s, "D", o.keyRight.isDown());
        int y = (s + gp) * 2, half = (totalW - gp) / 2;
        key(g, f, 0, y, half, 16, "LMB " + cps(leftClicks, now), l);
        key(g, f, half + gp, y, totalW - half - gp, 16, "RMB " + cps(rightClicks, now), r);
        key(g, f, 0, y + 16 + gp, totalW, 14, "SPACE", o.keyJump.isDown());
        g.pose().popMatrix();
    }

    // ---------- Modul-Liste ----------

    private static long glassCheckAt;
    private static boolean glassCached;

    private static void renderModList(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (!cfg.moduleList) return;
        long now = System.currentTimeMillis();
        if (now - glassCheckAt > 1000) {
            glassCheckAt = now;
            try { glassCached = PoldiGlass.isOn(); } catch (Exception e) { glassCached = false; }
        }
        List<String> m = new ArrayList<>();
        if (cfg.keystrokes) m.add("Keystrokes");
        if (cfg.coords) m.add("Koordinaten");
        if (cfg.showFps) m.add("FPS");
        if (cfg.ping) m.add("Ping");
        if (cfg.speed) m.add("Speed");
        if (cfg.clock) m.add("Uhr");
        if (cfg.fullbright) m.add("Fullbright");
        if (cfg.autoClick) m.add("Autoclicker " + cfg.autoCps);
        if (cfg.antiLeak) m.add("Anti-Leak");
        if (cfg.blockHighlight) m.add("Block-Highlight");
        if (cfg.itemGlow) m.add("Item-Highlight");
        if (cfg.lightOverlay) m.add("Licht-Overlay");
        if (cfg.dayHud) m.add("Tag-Zähler");
        if (cfg.twitch) m.add("Twitch-Chat");
        if (cfg.sellAuto) m.add("Sell-Makro Auto");
        if (PoldiMacro.isRunning()) m.add("Sell-Makro läuft");
        if (glassCached) m.add("Unsichtbares Glas");
        if (cfg.perfParticles) m.add("Partikel minimal");
        if (cfg.perfShadows) m.add("Schatten aus");
        if (cfg.perfDistance) m.add("Weniger Distanz");
        if (m.isEmpty()) return;
        m.sort((a, b) -> f.width(b) - f.width(a));

        int sw = g.guiWidth(), maxW = 0;
        for (String s : m) maxW = Math.max(maxW, f.width(s) + 8);
        float[] pos = place("mods", sw - 4 - maxW * cfg.hudScale, 4, maxW * cfg.hudScale, m.size() * 12 * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0] + maxW * cfg.hudScale, pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        int y = 0, n = Math.max(1, m.size() - 1);
        for (int i = 0; i < m.size(); i++) {
            String s = m.get(i);
            int w = f.width(s);
            int col = PoldiTheme.lerp(PoldiTheme.PRIMARY, PoldiTheme.SECOND, (float) i / n);
            PoldiTheme.rect(g, -w - 8, y, w + 8, 12, PoldiTheme.bgA(0xC0));
            PoldiTheme.rect(g, -2, y, 2, 12, col);
            PoldiTheme.text(g, f, s, -w - 5, y + 2, col);
            y += 12;
        }
        g.pose().popMatrix();
    }
}
