package dev.poldi.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Survival-Helfer: Todespunkt, Haltbarkeits-, Hunger- und Inventar-Warnung, Licht-Overlay (Mob-Spawn),
 * Tag-Zaehler mit Schlaf-Anzeige, Item-Pickup-Log, Biom und Nether-Koordinaten.
 */
public final class PoldiSurvival {
    private PoldiSurvival() {}

    // ---------- Todespunkt ----------

    private static boolean deathSaved;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DeathScreen) || deathSaved) return;
            PoldiConfig cfg = PoldiConfig.get();
            if (!cfg.deathWaypoint || client.player == null) return;
            deathSaved = true;
            int x = (int) Math.floor(client.player.getX()), y = (int) Math.floor(client.player.getY()), z = (int) Math.floor(client.player.getZ());
            PoldiWaypoints.add("Tod", x, y, z);
            PoldiClient.chatLong("Todespunkt gespeichert: " + x + " " + y + " " + z + " (Wegpunkt \"Tod\")");
        });
    }

    // ---------- Warnungen + Pickup-Log (pro Tick) ----------

    private static boolean hungerWarned, invWarned;
    private static final boolean[] toolWarned = new boolean[2];
    private static final Map<String, Integer> lastCounts = new HashMap<>();
    private static boolean haveCounts;
    public record Pickup(String name, int count, long at) {}
    public static final List<Pickup> PICKUPS = new ArrayList<>();

    public static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null) { haveCounts = false; deathSaved = false; return; }
        if (p.isAlive()) deathSaved = false;
        PoldiConfig cfg = PoldiConfig.get();

        // Hunger
        int food = p.getFoodData().getFoodLevel();
        if (cfg.hungerWarn && !p.isCreative() && !p.isSpectator() && food <= cfg.hungerWarnAt) {
            if (!hungerWarned) {
                hungerWarned = true;
                PoldiNotify.push("Hunger niedrig", "Nur noch " + food + "/20 – iss etwas!", PoldiTheme.WARN);
                if (cfg.sound) PoldiClient.sound("block.note_block.bass", 1f, 0.8f);
            }
        } else if (food > cfg.hungerWarnAt + 1) hungerWarned = false;

        // Inventar voll (Hauptinventar + Hotbar)
        int free = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) free++;
        if (cfg.invFullWarn && free == 0) {
            if (!invWarned) {
                invWarned = true;
                PoldiNotify.push("Inventar voll", "Neue Items werden nicht mehr aufgesammelt", PoldiTheme.WARN);
                if (cfg.sound) PoldiClient.sound("block.note_block.bit", 1f, 0.7f);
            }
        } else if (free > 0) invWarned = false;

        // Haltbarkeit von Werkzeug / Waffe in den Haenden
        EquipmentSlot[] hands = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        for (int i = 0; i < 2; i++) {
            ItemStack is = p.getItemBySlot(hands[i]);
            if (is.isEmpty() || !is.isDamageableItem()) { toolWarned[i] = false; continue; }
            int pct = Math.round(100f * (is.getMaxDamage() - is.getDamageValue()) / Math.max(1, is.getMaxDamage()));
            if (pct > cfg.toolWarnPct) { toolWarned[i] = false; continue; }
            if (!cfg.toolWarn || toolWarned[i]) continue;
            toolWarned[i] = true;
            PoldiNotify.push("Gleich kaputt!", is.getHoverName().getString() + " nur noch " + pct + " %", PoldiTheme.BAD);
            if (cfg.sound) PoldiClient.sound("entity.item.break", 0.8f, 1.2f);
        }

        // Pickup-Log: Zuwachs je Item im Inventar
        Map<String, Integer> now = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            String k = s.getItem().toString();
            now.merge(k, s.getCount(), Integer::sum);
            names.putIfAbsent(k, s.getHoverName().getString());
        }
        boolean inScreen = mc.gui.screen() != null; // Umsortieren in Kisten nicht als Pickup zaehlen
        if (haveCounts && cfg.pickupLog && !inScreen) {
            for (var e : now.entrySet()) {
                int diff = e.getValue() - lastCounts.getOrDefault(e.getKey(), 0);
                if (diff > 0) addPickup(names.get(e.getKey()), diff);
            }
        }
        lastCounts.clear();
        lastCounts.putAll(now);
        haveCounts = true;
        if (++lightTick % 10 == 0 && cfg.lightOverlay) scanLight(mc);
    }

    private static void addPickup(String name, int n) {
        long t = System.currentTimeMillis();
        for (int i = PICKUPS.size() - 1; i >= 0; i--) {
            Pickup q = PICKUPS.get(i);
            if (q.name().equals(name) && t - q.at() < 3000) { PICKUPS.set(i, new Pickup(name, q.count() + n, t)); return; }
        }
        PICKUPS.add(new Pickup(name, n, t));
        while (PICKUPS.size() > 6) PICKUPS.remove(0);
    }

    // ---------- Weltzeit / Tag-Zaehler ----------

    /** Weltzeit in Ticks (Tag 1 = 0..23999). Name der Methode aendert sich je nach Version. */
    public static long worldTime(Minecraft mc) {
        if (mc.level == null) return -1;
        Object v = PoldiR.call(mc.level, "getOverworldClockTime|getDefaultClockTime|getDayTime");
        return v instanceof Number n ? n.longValue() : -1;
    }

    public static boolean thunder(Minecraft mc) {
        Object v = PoldiR.call(mc.level, "isThundering");
        return v instanceof Boolean b && b;
    }

    public static String dayText(Minecraft mc) {
        long t = worldTime(mc);
        if (t < 0) return "?";
        return "Tag " + (t / 24000 + 1);
    }

    public static String clockText(Minecraft mc) {
        long t = worldTime(mc);
        if (t < 0) return "?";
        long tod = t % 24000;
        long h = (tod / 1000 + 6) % 24, m = (tod % 1000) * 60 / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
    }

    /** Schlafen moeglich: Nacht (ab Tick 12542) oder Gewitter. */
    public static String sleepText(Minecraft mc) {
        long t = worldTime(mc);
        if (t < 0) return "?";
        long tod = t % 24000;
        if ((tod >= 12542 && tod < 23460) || thunder(mc)) return "jetzt möglich";
        long wait = tod < 12542 ? 12542 - tod : 24000 - tod + 12542;
        long sec = wait / 20;
        return "in " + sec / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60);
    }

    // ---------- Biom + Nether-Koordinaten ----------

    public static String biome(Minecraft mc) {
        if (mc.level == null || mc.player == null) return "?";
        try {
            var h = mc.level.getBiome(mc.player.blockPosition());
            String path = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
            StringBuilder sb = new StringBuilder();
            for (String w : path.split("_")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            return sb.toString().trim();
        } catch (Throwable t) { return "?"; }
    }

    /** "Overworld: x z" im Nether bzw. "Nether: x z" in der Oberwelt, sonst null. */
    public static String otherDim(Minecraft mc) {
        if (mc.player == null) return null;
        String dim = PoldiWaypoints.dim(mc);
        double x = mc.player.getX(), z = mc.player.getZ();
        if (dim.endsWith("the_nether")) return "Oberwelt " + (int) Math.floor(x * 8) + " " + (int) Math.floor(z * 8);
        if (dim.endsWith("overworld")) return "Nether " + (int) Math.floor(x / 8) + " " + (int) Math.floor(z / 8);
        return null;
    }

    // ---------- Licht ----------

    private static int lightTick;
    /** {x, y, z, typ}: typ 2 = Blocklicht 0 und kein Himmel (immer Spawn), 1 = Blocklicht 0, Himmel offen (nur nachts). */
    private static final List<int[]> SPOTS = new ArrayList<>();

    private static void scanLight(Minecraft mc) {
        SPOTS.clear();
        if (mc.level == null || mc.player == null) return;
        var lv = mc.level;
        BlockPos c = mc.player.blockPosition();
        int r = 8;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), below = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -4; dy <= 3; dy++) {
            pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            below.set(pos.getX(), pos.getY() - 1, pos.getZ());
            BlockState st = lv.getBlockState(pos);
            if (!st.getCollisionShape(lv, pos).isEmpty() || !st.getFluidState().isEmpty()) continue;
            BlockState bs = lv.getBlockState(below);
            if (!bs.isFaceSturdy(lv, below, Direction.UP)) continue;
            if (lv.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
            int sky = lv.getBrightness(LightLayer.SKY, pos);
            SPOTS.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), sky > 0 ? 1 : 2});
            if (SPOTS.size() > 600) return;
        }
    }

    /** Kleine Markierungen auf Bloecken, auf denen Monster spawnen koennen (rot = immer, gelb = nur nachts). */
    public static void lightOverlay(GuiGraphicsExtractor g, Minecraft mc) {
        for (int[] s : SPOTS) {
            float[] p = PoldiEsp.toScreen(s[0] + 0.5, s[1] + 0.02, s[2] + 0.5);
            if (p == null) continue;
            int x = (int) p[0], y = (int) p[1];
            int col = s[3] == 2 ? 0xC0FF3030 : 0xB0FFC832;
            g.fill(x - 2, y - 2, x + 2, y + 2, col);
        }
    }

    public static String lightText(Minecraft mc) {
        if (mc.level == null || mc.player == null) return "?";
        BlockPos bp = mc.player.blockPosition();
        int b = mc.level.getBrightness(LightLayer.BLOCK, bp), s = mc.level.getBrightness(LightLayer.SKY, bp);
        return "Block " + b + " · Himmel " + s;
    }

    // ---------- HUD ----------

    public static void render(GuiGraphicsExtractor g, Font f, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null) return;
        if (cfg.dayHud) {
            List<PoldiPanels.Row> rows = new ArrayList<>();
            rows.add(new PoldiPanels.Row(dayText(mc), "", clockText(mc), PoldiTheme.TEXT));
            String sl = sleepText(mc);
            rows.add(new PoldiPanels.Row("Schlafen", "", sl, sl.startsWith("jetzt") ? PoldiTheme.GOOD : PoldiTheme.MUTED));
            PoldiPanels.panel(g, f, mc, cfg, "day", "Zeit", rows, g.guiWidth() - 130, 6, 110);
        }
        if (cfg.lightHud) {
            List<PoldiPanels.Row> rows = new ArrayList<>();
            BlockPos bp = mc.player.blockPosition();
            int b = mc.level.getBrightness(LightLayer.BLOCK, bp);
            rows.add(new PoldiPanels.Row("Licht", "", lightText(mc), b == 0 ? PoldiTheme.BAD : PoldiTheme.TEXT));
            if (b == 0) rows.add(new PoldiPanels.Row("Monster können hier spawnen", "", "", PoldiTheme.BAD));
            PoldiPanels.panel(g, f, mc, cfg, "light", "Licht", rows, g.guiWidth() - 170, 48, 150);
        }
        if (cfg.pickupLog && !PICKUPS.isEmpty()) {
            long now = System.currentTimeMillis();
            PICKUPS.removeIf(q -> now - q.at() > 4000);
            int y = g.guiHeight() / 2 + 20, x = g.guiWidth() / 2 + 20;
            float[] pos = PoldiHud.place("pickup", x, y, 140 * cfg.hudScale, 6 * 11 * cfg.hudScale, mc);
            g.pose().pushMatrix();
            g.pose().translate(pos[0], pos[1]);
            g.pose().scale(cfg.hudScale, cfg.hudScale);
            int yy = 0;
            for (Pickup q : PICKUPS) {
                float age = (now - q.at()) / 4000f;
                int a = (int) (255 * Math.min(1f, (1f - age) * 3f));
                if (a < 10) continue;
                PoldiTheme.text(g, f, "+" + q.count() + " " + q.name(), 0, yy, PoldiTheme.alpha(PoldiTheme.GOOD, a));
                yy += 11;
            }
            g.pose().popMatrix();
        }
    }
}
