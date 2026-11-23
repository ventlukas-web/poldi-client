package dev.poldi.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/** PvP-Anzeigen: CPS, Reach, Item-Zaehler, Treffer-Marker/-Sound, Low-HP-Warnung, Cooldown-Balken. Nur Anzeige. */
public final class PoldiPvp {
    private static final ArrayDeque<Long> L = new ArrayDeque<>(), R = new ArrayDeque<>();
    private static boolean pl, pr;
    public static double reach = -1;
    private static long markerUntil;
    public static final int[] COUNTS = new int[6];
    public static final String[] NAMES = {"Totems", "G-Äpfel", "Perlen", "Kristalle", "Obsidian", "Tränke"};

    /** {Name, Sound-ID, Lautstaerke, Tonhoehe} */
    public static final Object[][] SOUNDS = {
        {"Ding", "entity.arrow.hit_player", 0.8f, 1.35f},
        {"Pling", "block.note_block.pling", 0.8f, 1.8f},
        {"Klick", "block.note_block.hat", 0.9f, 1.6f},
        {"Orb", "entity.experience_orb.pickup", 0.7f, 1.4f},
        {"Glocke", "block.note_block.bell", 0.8f, 1.6f},
    };

    public static void playHit() {
        Object[] s = SOUNDS[Math.floorMod(PoldiConfig.get().hitSoundIdx, SOUNDS.length)];
        PoldiClient.sound((String) s[1], (Float) s[2], (Float) s[3]);
    }

    private PoldiPvp() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (level.isClientSide() && player == mc.player && entity != null) {
                Vec3 eye = player.getEyePosition();
                AABB b = entity.getBoundingBox();
                Vec3 c = new Vec3(clamp(eye.x, b.minX, b.maxX), clamp(eye.y, b.minY, b.maxY), clamp(eye.z, b.minZ, b.maxZ));
                reach = eye.distanceTo(c);
                markerUntil = System.currentTimeMillis() + 260;
                if (PoldiConfig.get().hitSound) playHit();
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(PoldiPvp::tick);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (mc.gui.screen() == null) {
            long win = mc.getWindow().handle();
            boolean l = GLFW.glfwGetMouseButton(win, 0) == GLFW.GLFW_PRESS, r = GLFW.glfwGetMouseButton(win, 1) == GLFW.GLFW_PRESS;
            if (l && !pl) L.add(now);
            if (r && !pr) R.add(now);
            pl = l; pr = r;
        } else { pl = false; pr = false; }
        while (!L.isEmpty() && now - L.peekFirst() > 1000) L.pollFirst();
        while (!R.isEmpty() && now - R.peekFirst() > 1000) R.pollFirst();
        if (mc.player.tickCount % 5 == 0 && PoldiConfig.get().pvpItems) count(mc);
    }

    private static void count(Minecraft mc) {
        java.util.Arrays.fill(COUNTS, 0);
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            int n = s.getCount();
            if (it == Items.TOTEM_OF_UNDYING) COUNTS[0] += n;
            else if (it == Items.GOLDEN_APPLE || it == Items.ENCHANTED_GOLDEN_APPLE) COUNTS[1] += n;
            else if (it == Items.ENDER_PEARL) COUNTS[2] += n;
            else if (it == Items.END_CRYSTAL) COUNTS[3] += n;
            else if (it == Items.OBSIDIAN) COUNTS[4] += n;
            else if (it == Items.SPLASH_POTION) COUNTS[5] += n;
        }
    }

    public static int cpsL() { return L.size(); }
    public static int cpsR() { return R.size(); }

    /** Treffer-Marker, Cooldown-Balken, Low-HP-Warnung. */
    public static void overlay(GuiGraphicsExtractor g, Minecraft mc, PoldiConfig cfg) {
        if (mc.player == null || mc.gui.screen() != null) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        long now = System.currentTimeMillis();
        int cx = sw / 2, cy = sh / 2;

        if (cfg.hitMarker && now < markerUntil) {
            int a = (int) (255 * (markerUntil - now) / 260.0);
            int col = PoldiTheme.alpha(0xFFFFFFFF, Math.max(40, a));
            for (int d = 4; d <= 8; d++) {
                g.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, col);
                g.fill(cx + d - 1, cy - d, cx + d, cy - d + 1, col);
                g.fill(cx - d, cy + d - 1, cx - d + 1, cy + d, col);
                g.fill(cx + d - 1, cy + d - 1, cx + d, cy + d, col);
            }
        }
        if (cfg.cooldownBar) {
            float p = mc.player.getAttackStrengthScale(0f);
            if (p < 0.999f) {
                int w = 34, x = cx - w / 2, y = cy + 14;
                g.fill(x - 1, y - 1, x + w + 1, y + 4, 0x90000000);
                g.fill(x, y, x + (int) (w * p), y + 3, p > 0.9f ? 0xFF86EFAC : 0xFFFBBF24);
            }
        }
        if (cfg.lowHpWarn) {
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (hp > 0 && hp <= 6f) {
                double pulse = 0.5 + 0.5 * Math.sin(now / 220.0);
                int a = (int) (0x28 + 0x40 * pulse * (1f - hp / 8f));
                int red = 0xFFDC2626;
                g.fillGradient(0, 0, sw, 46, PoldiTheme.alpha(red, a), 0x00DC2626);
                g.fillGradient(0, sh - 46, sw, sh, 0x00DC2626, PoldiTheme.alpha(red, a));
            }
        }
    }
}
