package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

/** Zusaetzliche HUD-Panels (alles ausser Gambling). */
public final class PoldiPanels {
    private PoldiPanels() {}

    public record Row(String left, String mid, String right, int color) {}

    /** Goldener Rand um die Hunger-Icons: zeigt die Saturation. */
    private static void saturation(GuiGraphicsExtractor g, Minecraft mc) {
        var pl = mc.player;
        if (pl == null || pl.isCreative() || pl.isSpectator()) return;
        if (pl.getVehicle() instanceof LivingEntity) return;
        float sat = Math.min(pl.getFoodData().getSaturationLevel(), pl.getFoodData().getFoodLevel());
        if (sat <= 0.01f) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int baseX = sw / 2 + 91 - 9, y = sh - 39;
        int full = (int) (sat / 2f);
        boolean half = sat - full * 2f >= 1f;
        int gold = 0xFFFFC832;
        for (int k = 0; k < 10 && k <= full; k++) {
            boolean f = k < full;
            if (!f && !(k == full && half)) break;
            int x = baseX - k * 8;
            int c = f ? gold : PoldiTheme.alpha(gold, 0x90);
            g.fill(x - 1, y - 1, x + 10, y, c);
            g.fill(x - 1, y + 9, x + 10, y + 10, c);
            g.fill(x - 1, y, x, y + 9, c);
            g.fill(x + 9, y, x + 10, y + 9, c);
        }
    }

    private static void pvpPanel(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        List<Row> rows = new ArrayList<>();
        if (cfg.cpsHud) rows.add(new Row("CPS", "", PoldiPvp.cpsL() + " | " + PoldiPvp.cpsR(), PoldiTheme.TEXT));
        if (cfg.reachHud) rows.add(new Row("Reach", "", PoldiPvp.reach < 0 ? "–" : String.format(java.util.Locale.ROOT, "%.2f", PoldiPvp.reach),
            PoldiPvp.reach > 3.0 ? PoldiTheme.BAD : PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "pvp", "PvP", rows, 6, g.guiHeight() / 2f - 20, 90);
    }

    private static void pvpItems(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < PoldiPvp.NAMES.length; i++)
            rows.add(new Row(PoldiPvp.NAMES[i], "", String.valueOf(PoldiPvp.COUNTS[i]), PoldiPvp.COUNTS[i] == 0 ? PoldiTheme.MUTED : PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "pvpitems", "PvP-Items", rows, 6, g.guiHeight() / 2f + 30, 90);
    }

    public static void render(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg, float delta) {
        if (cfg.satBorder) saturation(g, mc);
        PoldiPvp.overlay(g, mc, cfg);
        if (cfg.cpsHud || cfg.reachHud) pvpPanel(g, f, mc, cfg);
        if (cfg.pvpItems) pvpItems(g, f, mc, cfg);
        events(g, f, mc, cfg);
        if (cfg.wpOn && !cfg.waypoints.isEmpty()) waypoints(g, f, mc, cfg);
        if (cfg.customHud) custom(g, f, mc, cfg);
        if (cfg.nearbyHud) nearby(g, f, mc, cfg);
        if (cfg.targetHud) target(g, f, mc, cfg);
        if (cfg.comboHud) combo(g, f, mc, cfg);
        if (cfg.memHud) mem(g, f, mc, cfg);
        if (cfg.vitalsHud) vitals(g, f, mc, cfg);
        if (cfg.armorHud) armor(g, f, mc, cfg);
        if (cfg.compassHud) compass(g, f, mc, cfg);
        if (cfg.sellHud) sellStatus(g, f, mc, cfg);
        if (cfg.potionHud) potions(g, f, mc, cfg);
        if (cfg.sessionHud) session(g, f, mc, cfg);
        if (cfg.notesHud) notes(g, f, mc, cfg);
        PoldiSurvival.render(g, f, mc, cfg);
        if (cfg.twitch || cfg.youtube) twitch(g, f, mc, cfg);
        PoldiEsp.render(g, mc, cfg, delta);
        if (cfg.notify) PoldiNotify.render(g, f, mc);
    }

    public static void panel(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg, String id, String title,
                             List<Row> rows, float defX, float defY, int minW) {
        int w = Math.max(minW, f.width(title) + 4);
        for (Row r : rows) w = Math.max(w, f.width(r.left()) + 12 + f.width(r.mid()) + (r.mid().isEmpty() ? 0 : 10) + f.width(r.right()));
        int pad = 6, h = 17 + rows.size() * 11 + pad;
        float[] pos = PoldiHud.place(id, defX < 0 ? (g.guiWidth() - (w + pad * 2) * cfg.hudScale) / 2f : defX, defY,
            (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        PoldiTheme.panel(g, 0, 0, w + pad * 2, h);
        PoldiTheme.gradientText(g, f, title, pad, 5);
        int y = 17;
        for (Row r : rows) {
            PoldiTheme.text(g, f, r.left(), pad, y, r.color());
            if (!r.mid().isEmpty()) PoldiTheme.text(g, f, r.mid(), pad + f.width(r.left()) + 10, y, PoldiTheme.TEXT);
            PoldiTheme.text(g, f, r.right(), w + pad - f.width(r.right()), y, r.color());
            y += 11;
        }
        g.pose().popMatrix();
    }

    private static void waypoints(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null || mc.level == null) return;
        String dim = PoldiWaypoints.dim(mc);
        List<PoldiConfig.Wp> ws = new ArrayList<>();
        for (var w : cfg.waypoints) if (dim.equals(w.dim)) ws.add(w);
        ws.sort((a, b) -> Double.compare(PoldiWaypoints.dist(mc, a), PoldiWaypoints.dist(mc, b)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(6, ws.size()); i++) {
            var w = ws.get(i);
            rows.add(new Row(w.name, "", PoldiWaypoints.arrow(mc, w) + " " + Math.round(PoldiWaypoints.dist(mc, w)) + "m",
                w.name.equals("Tod") ? PoldiTheme.BAD : PoldiTheme.TEXT));
        }
        if (rows.isEmpty()) rows.add(new Row("keine Wegpunkte hier", "", "", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "waypoints", "Wegpunkte", rows, 6, g.guiHeight() / 2f + 60, 120);
    }

    public static final String[] TOKENS = {"{x}", "{y}", "{z}", "{fps}", "{ping}", "{zeit}", "{leben}", "{hunger}", "{combo}",
        "{name}", "{slots}", "{tag}", "{biom}"};

    private static String fill(String s, Minecraft mc) {
        var p = mc.player;
        if (p == null || !s.contains("{")) return s;
        int free = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) free++;
        int ping = 0;
        if (mc.getConnection() != null) { var e = mc.getConnection().getPlayerInfo(p.getUUID()); if (e != null) ping = e.getLatency(); }
        PoldiConfig c = PoldiConfig.get();
        boolean hide = c.streamer && c.streamerHideXyz;
        String[] v = {hide ? "?" : String.valueOf((int) Math.floor(p.getX())), hide ? "?" : String.valueOf((int) Math.floor(p.getY())), hide ? "?" : String.valueOf((int) Math.floor(p.getZ())),
            String.valueOf(mc.getFps()), String.valueOf(ping),
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            String.format(java.util.Locale.ROOT, "%.1f", p.getHealth()), String.valueOf(p.getFoodData().getFoodLevel()),
            String.valueOf(PoldiMisc.combo), c.streamer ? c.streamerName : p.getName().getString(), String.valueOf(free),
            PoldiSurvival.dayText(mc), PoldiSurvival.biome(mc)};
        for (int i = 0; i < TOKENS.length; i++) s = s.replace(TOKENS[i], v[i]);
        return s;
    }

    private static void custom(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        List<Row> rows = new ArrayList<>();
        for (String l : cfg.customLines) if (l != null && !l.isBlank()) rows.add(new Row(fill(l, mc), "", "", PoldiTheme.TEXT));
        if (rows.isEmpty()) rows.add(new Row("Zeilen im Menü eintragen", "", "", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "custom", cfg.customTitle == null || cfg.customTitle.isBlank() ? "Mein HUD" : cfg.customTitle, rows,
            g.guiWidth() - 190, 120, 130);
    }

    private static void nearby(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.level == null || mc.player == null) return;
        List<Player> ps = new ArrayList<>();
        for (var p : mc.level.players()) if (p != mc.player) ps.add(p);
        ps.sort((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(8, ps.size()); i++) {
            var p = ps.get(i);
            float d = p.distanceTo(mc.player);
            rows.add(new Row(p.getName().getString(), "", Math.round(d) + "m", d < 16 ? PoldiTheme.BAD : PoldiTheme.TEXT));
        }
        if (ps.size() > 8) rows.add(new Row("+" + (ps.size() - 8) + " weitere", "", "", PoldiTheme.MUTED));
        if (rows.isEmpty()) rows.add(new Row("niemand in der Nähe", "", "", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "nearby", "Spieler", rows, g.guiWidth() - 130, g.guiHeight() / 2f + 40, 110);
    }

    private static LivingEntity lastTarget;
    private static long lastTargetAt;

    /** Ziel-HUD = Mob-/Spieler-Leben des anvisierten Wesens. */
    private static void target(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        long now = System.currentTimeMillis();
        if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) { lastTarget = le; lastTargetAt = now; }
        List<Row> rows = new ArrayList<>();
        if (lastTarget != null && lastTarget.isAlive() && now - lastTargetAt < 2000) {
            float hp = lastTarget.getHealth(), max = Math.max(1f, lastTarget.getMaxHealth());
            rows.add(new Row(lastTarget.getName().getString(), "", "", PoldiTheme.TEXT));
            rows.add(new Row("Leben", "", String.format(java.util.Locale.ROOT, "%.1f / %.0f", hp, max),
                PoldiTheme.lerp(PoldiTheme.BAD, PoldiTheme.GOOD, Math.min(1f, hp / max))));
            if (mc.player != null) rows.add(new Row("Distanz", "", String.format(java.util.Locale.ROOT, "%.1f m", lastTarget.distanceTo(mc.player)), PoldiTheme.MUTED));
        } else rows.add(new Row("kein Ziel", "", "", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "target", "Ziel", rows, g.guiWidth() / 2f - 60, g.guiHeight() / 2f + 30, 120);
    }

    private static void combo(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        long now = System.currentTimeMillis();
        if (now - PoldiMisc.lastHit > 2500 || (mc.player != null && mc.player.hurtTime > 0)) PoldiMisc.combo = 0;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Combo", "", String.valueOf(PoldiMisc.combo), PoldiMisc.combo > 0 ? PoldiTheme.GOOD : PoldiTheme.MUTED));
        rows.add(new Row("Bester Combo", "", String.valueOf(PoldiMisc.maxCombo), PoldiTheme.TEXT));
        rows.add(new Row("Treffer", "", String.valueOf(PoldiMisc.hits), PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "combo", "Combo", rows, g.guiWidth() / 2f + 70, g.guiHeight() / 2f - 30, 110);
    }

    private static void mem(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("FPS", "", String.valueOf(mc.getFps()), PoldiTheme.TEXT));
        rows.add(new Row("RAM", "", used + " / " + max + " MB", used * 10 > max * 9 ? PoldiTheme.BAD : PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "mem", "System", rows, 6, g.guiHeight() - 80, 110);
    }

    private static void vitals(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        var p = mc.player;
        if (p == null) return;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Leben", "", String.format(java.util.Locale.ROOT, "%.1f", p.getHealth() + p.getAbsorptionAmount()),
            PoldiTheme.lerp(PoldiTheme.BAD, PoldiTheme.GOOD, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())))));
        rows.add(new Row("Hunger", "", String.valueOf(p.getFoodData().getFoodLevel()), PoldiTheme.TEXT));
        rows.add(new Row("XP-Level", "", String.valueOf(p.experienceLevel), PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "vitals", "Status", rows, g.guiWidth() / 2f - 160, g.guiHeight() - 70, 100);
    }

    private static final long START = System.currentTimeMillis();

    private static void armor(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null) return;
        EquipmentSlot[] sl = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        List<ItemStack> st = new ArrayList<>();
        for (var s : sl) { var is = mc.player.getItemBySlot(s); if (!is.isEmpty()) st.add(is); }
        int w = 112, rows = Math.max(1, st.size()), h = rows * 18 + 8;
        float[] pos = PoldiHud.place("armor", g.guiWidth() - w - 6, g.guiHeight() - h - 30, w * cfg.hudScale, h * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        PoldiTheme.panel(g, 0, 0, w, h);
        int y = 4;
        if (st.isEmpty()) PoldiTheme.text(g, f, "Haltbarkeit", 8, 8, PoldiTheme.MUTED);
        for (var is : st) {
            g.item(is, 5, y);
            String t = is.isDamageableItem() ? Math.round(100f * (is.getMaxDamage() - is.getDamageValue()) / Math.max(1, is.getMaxDamage())) + " %  ·  " + (is.getMaxDamage() - is.getDamageValue()) : (is.getCount() > 1 ? "x" + is.getCount() : "");
            float frac = is.isDamageableItem() ? 1f - (float) is.getDamageValue() / Math.max(1, is.getMaxDamage()) : 1f;
            PoldiTheme.text(g, f, t, 26, y + 5, PoldiTheme.lerp(PoldiTheme.BAD, PoldiTheme.GOOD, frac));
            y += 18;
        }
        g.pose().popMatrix();
    }

    private static final boolean[] armorWarned = new boolean[4];
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"Helm", "Brustplatte", "Hose", "Schuhe"};

    /** Warnt (Toast + Ton) einmal, wenn ein Ruestungsteil unter die eingestellte Haltbarkeit faellt. */
    public static void armorCheck() {
        Minecraft mc = Minecraft.getInstance();
        PoldiConfig cfg = PoldiConfig.get();
        if (mc.player == null) return;
        for (int i = 0; i < 4; i++) {
            var is = mc.player.getItemBySlot(ARMOR_SLOTS[i]);
            if (is.isEmpty() || !is.isDamageableItem()) { armorWarned[i] = false; continue; }
            int pct = Math.round(100f * (is.getMaxDamage() - is.getDamageValue()) / Math.max(1, is.getMaxDamage()));
            if (pct > cfg.armorWarnPct) { armorWarned[i] = false; continue; }
            if (!cfg.armorWarn || armorWarned[i]) continue;
            armorWarned[i] = true;
            PoldiNotify.push("Rüstung fast kaputt", ARMOR_NAMES[i] + " nur noch " + pct + " %", PoldiTheme.BAD);
            if (cfg.sound) PoldiClient.sound("block.anvil.land");
        }
    }

    private static void sellStatus(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        long sold = PoldiMacro.soldItems;
        double perMin = 0;
        if (PoldiMacro.soldSince > 0) perMin = sold / Math.max(1.0 / 60, (System.currentTimeMillis() - PoldiMacro.soldSince) / 60000.0);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Status", "", PoldiMacro.statusText(), PoldiMacro.isRunning() ? PoldiTheme.GOOD : PoldiTheme.TEXT));
        rows.add(new Row("Durchläufe", "", String.valueOf(PoldiMacro.cycleCount()), PoldiTheme.TEXT));
        rows.add(new Row("Verkauft", "", sold + " Items", PoldiTheme.TEXT));
        rows.add(new Row("Tempo", "", Math.round(perMin) + " Items/min", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "sellstatus", "Sell Status", rows, 6, g.guiHeight() / 2f - 130, 120);
    }

    private static final String[] DIRS = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};

    private static void compass(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null) return;
        int w = 200, h = 27;
        float[] pos = PoldiHud.place("compass", (g.guiWidth() - w * cfg.hudScale) / 2f, g.guiHeight() - 78, w * cfg.hudScale, h * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        PoldiTheme.panel(g, 0, 0, w, h, 0xB0);
        double bear = ((mc.player.getYRot() + 180) % 360 + 360) % 360;
        String head = Math.round(bear) % 360 + "°";
        PoldiTheme.text(g, f, head, (w - f.width(head)) / 2, 3, PoldiTheme.MUTED);
        double range = 90, half = w / 2.0 - 10;
        for (int b = -105; b <= 105; b += 15) {
            double abs = Math.floor(bear / 15) * 15 + b;
            double off = abs - bear;
            if (Math.abs(off) > range) continue;
            int px = (int) Math.round(w / 2.0 + off / range * half);
            int ab = (int) (((Math.round(abs) % 360) + 360) % 360);
            if (ab % 45 == 0) {
                String d = DIRS[ab / 45];
                PoldiTheme.text(g, f, d, px - f.width(d) / 2, 13, ab == 0 ? PoldiTheme.PRIMARY : PoldiTheme.TEXT);
            } else g.fill(px, 15, px + 1, 19, PoldiTheme.alpha(PoldiTheme.MUTED, 0xA0));
        }
        if (cfg.wpOn) {
            String dim = PoldiWaypoints.dim(mc);
            for (var wp : cfg.waypoints) {
                if (!dim.equals(wp.dim)) continue;
                double dx = wp.x + 0.5 - mc.player.getX(), dz = wp.z + 0.5 - mc.player.getZ();
                double wb = ((Math.toDegrees(Math.atan2(-dx, dz)) + 180) % 360 + 360) % 360;
                double off = ((wb - bear + 540) % 360) - 180;
                boolean edge = Math.abs(off) > range;
                double o = Math.max(-range, Math.min(range, off));
                int px = (int) Math.round(w / 2.0 + o / range * half);
                g.fill(px - 1, 21, px + 2, 24, edge ? PoldiTheme.alpha(0xFFFBBF24, 0x70) : 0xFFFBBF24);
            }
        }
        g.fill(w / 2, 22, w / 2 + 1, 26, PoldiTheme.SECOND);
        g.pose().popMatrix();
    }

    private static void potions(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null) return;
        List<Row> rows = new ArrayList<>();
        for (var e : mc.player.getActiveEffects()) {
            var type = e.getEffect().value();
            String name = type.getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int d = e.getDuration();
            String time = d < 0 || d > 100000 ? "∞" : String.format(java.util.Locale.ROOT, "%d:%02d", d / 1200, (d / 20) % 60);
            rows.add(new Row(name, "", time, type.isBeneficial() ? PoldiTheme.GOOD : PoldiTheme.BAD));
        }
        if (rows.isEmpty()) rows.add(new Row("keine Effekte", "", "", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "potions", "Effekte", rows, g.guiWidth() - 140, 60, 110);
    }

    private static void session(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null) return;
        long sec = (System.currentTimeMillis() - START) / 1000;
        int free = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Spielzeit", "", String.format(java.util.Locale.ROOT, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60), PoldiTheme.TEXT));
        rows.add(new Row("Freie Slots", "", free + "/36", free == 0 ? PoldiTheme.BAD : PoldiTheme.TEXT));
        rows.add(new Row("Welt-Zeit", "", PoldiSurvival.clockText(mc), PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "session", "Session", rows, 6, g.guiHeight() / 2f - 60, 120);
    }

    private static void notes(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        List<Row> rows = new ArrayList<>();
        int n = cfg.notes.size();
        for (int i = 0; i < Math.min(n, 10); i++) rows.add(new Row((i + 1) + ". " + f.plainSubstrByWidth(cfg.notes.get(i), 170), "", "", PoldiTheme.TEXT));
        if (n > 10) rows.add(new Row("+" + (n - 10) + " weitere", "", "", PoldiTheme.MUTED));
        if (rows.isEmpty()) rows.add(new Row("/note add <Text>", "", "", PoldiTheme.MUTED));
        panel(g, f, mc, cfg, "notes", "Notizen", rows, g.guiWidth() - 190, g.guiHeight() / 2f, 150);
    }

    private static void events(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        List<String[]> r = PoldiEvents.rows();
        if (r.isEmpty()) return;
        List<Row> rows = new ArrayList<>();
        for (String[] s : r) rows.add(new Row(s[0], "", s[1], PoldiTheme.TEXT));
        panel(g, f, mc, cfg, "events", "Events", rows, 6, 120, 120);
    }

    private static void twitch(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        List<PoldiTwitch.Msg> m = PoldiTwitch.last(Math.max(3, Math.min(15, cfg.twitchLines)));
        int w = 220, pad = 6, h = 17 + Math.max(1, m.size()) * 11 + pad;
        float[] pos = PoldiHud.place("twitch", 6, g.guiHeight() / 2f, (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(cfg.hudScale, cfg.hudScale);
        PoldiTheme.panel(g, 0, 0, w + pad * 2, h);
        PoldiTheme.gradientText(g, f, cfg.twitch && cfg.youtube ? "Chat" : cfg.youtube ? "YouTube" : "Twitch", pad, 5);
        String st = cfg.twitch && cfg.youtube ? "Twitch + YouTube" : cfg.youtube ? PoldiYoutube.state : PoldiTwitch.state;
        PoldiTheme.text(g, f, st, w + pad - f.width(st), 5, PoldiTheme.MUTED);
        int y = 17;
        if (m.isEmpty()) PoldiTheme.text(g, f, (cfg.youtube ? cfg.youtubeKey.isBlank() || cfg.youtubeVideo.isBlank() : cfg.twitchChannel.isBlank()) ? "Kanal/Video im Menü setzen" : "warte auf Nachrichten …", pad, y, PoldiTheme.MUTED);
        for (var e : m) {
            String name = e.user() + ": ";
            PoldiTheme.text(g, f, name, pad, y, e.color());
            PoldiTheme.text(g, f, f.plainSubstrByWidth(e.text(), Math.max(10, w - f.width(name))), pad + f.width(name), y, PoldiTheme.TEXT);
            y += 11;
        }
        g.pose().popMatrix();
    }
}
