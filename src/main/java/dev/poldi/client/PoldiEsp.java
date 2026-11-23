package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Block-/Item-Highlight, Wegpunkt-Marker, eigener Nametag und Licht-Overlay: 3D-Punkte werden auf den Bildschirm projiziert. */
public final class PoldiEsp {
    private PoldiEsp() {}

    private static double camX, camY, camZ, cy, cp, th, aspect;
    private static int sw, sh;

    /** Liest Kameraposition und -winkel (Namen aendern sich zwischen Versionen, daher per Reflection). */
    private static boolean camera(Minecraft mc, float delta) {
        Object cam = PoldiR.get(mc.gameRenderer, "getMainCamera|mainCamera|camera");
        Object pos = cam == null ? null : PoldiR.get(cam, "position|getPosition|pos");
        if (pos instanceof Vec3 v) { camX = v.x; camY = v.y; camZ = v.z; }
        else {
            Entity ce = mc.getCameraEntity();
            if (ce == null) return false;
            Vec3 e = ce.getEyePosition(delta);
            camX = e.x; camY = e.y; camZ = e.z;
        }
        double yaw = PoldiR.num(cam == null ? null : PoldiR.get(cam, "yRot|getYRot"), Double.NaN);
        double pitch = PoldiR.num(cam == null ? null : PoldiR.get(cam, "xRot|getXRot"), Double.NaN);
        if (Double.isNaN(yaw) || Double.isNaN(pitch)) {
            Entity ce = mc.getCameraEntity();
            if (ce == null) return false;
            yaw = ce.getYRot(); pitch = ce.getXRot();
        }
        cy = Math.toRadians(yaw); cp = Math.toRadians(pitch);
        sw = mc.getWindow().getGuiScaledWidth(); sh = mc.getWindow().getGuiScaledHeight();
        th = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        aspect = (double) sw / sh;
        return true;
    }

    static float[] toScreen(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double fx = -Math.sin(cy) * Math.cos(cp), fy = -Math.sin(cp), fz = Math.cos(cy) * Math.cos(cp);
        double rx = -Math.cos(cy), rz = -Math.sin(cy);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc < 0.05) return null;
        double xc = dx * rx + dz * rz, yc = dx * ux + dy * uy + dz * uz;
        return new float[]{(float) (sw / 2.0 * (1 + xc / (zc * th * aspect))), (float) (sh / 2.0 * (1 - yc / (zc * th)))};
    }

    /** Eine Linie = ein gedrehtes Rechteck. */
    private static void line(GuiGraphicsExtractor g, float[] a, float[] b, int color, int t) {
        if (a == null || b == null) return;
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f || len > 5000f) return;
        g.pose().pushMatrix();
        g.pose().translate(a[0], a[1]);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.fill(0, 0, (int) Math.ceil(len), t, color);
        g.pose().popMatrix();
    }

    private static void box(GuiGraphicsExtractor g, AABB b, int color, int t) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        float[][] p = new float[8][];
        for (int i = 0; i < 8; i++) p[i] = toScreen(xs[i & 1], ys[(i >> 1) & 1], zs[(i >> 2) & 1]);
        int[][] e = {{0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
        for (int[] k : e) line(g, p[k[0]], p[k[1]], color, t);
    }

    public static void render(GuiGraphicsExtractor g, Minecraft mc, PoldiConfig cfg, float delta) {
        boolean wps = cfg.wpOn && !cfg.waypoints.isEmpty();
        boolean ownTag = cfg.ownNametag && mc.player != null && !mc.options.getCameraType().isFirstPerson();
        if (!cfg.blockHighlight && !cfg.itemGlow && !wps && !ownTag && !cfg.lightOverlay) return;
        if (mc.level == null || !camera(mc, delta)) return;
        Font f = mc.font;

        if (ownTag) {
            var pl = mc.player;
            Vec3 pp = pl.getPosition(delta);
            float[] p = toScreen(pp.x, pp.y + pl.getBbHeight() + 0.55, pp.z);
            if (p != null) {
                String t = pl.getDisplayName().getString();
                int tw = f.width(t), x = (int) p[0], y = (int) p[1];
                PoldiTheme.rect(g, x - tw / 2 - 4, y - 6, tw + 8, 12, 0x90000000);
                PoldiTheme.outline(g, x - tw / 2 - 4, y - 6, tw + 8, 12, PoldiTheme.alpha(PoldiTheme.PRIMARY, 0xB0));
                PoldiTheme.text(g, f, t, x - tw / 2, y - 4, 0xFFFFFFFF);
            }
        }
        if (wps && mc.player != null) {
            String dim = PoldiWaypoints.dim(mc);
            for (PoldiConfig.Wp w : cfg.waypoints) {
                if (!dim.equals(w.dim)) continue;
                float[] p = toScreen(w.x + 0.5, w.y + 1.2, w.z + 0.5);
                if (p == null || p[0] < -20 || p[0] > sw + 20 || p[1] < -20 || p[1] > sh + 20) continue;
                String t = w.name + "  " + Math.round(PoldiWaypoints.dist(mc, w)) + "m";
                int tw = f.width(t), x = (int) p[0], y = (int) p[1];
                g.fill(x - 2, y + 7, x + 3, y + 12, PoldiTheme.SECOND);
                PoldiTheme.rect(g, x - tw / 2 - 4, y - 6, tw + 8, 12, PoldiTheme.bgA(0xC0));
                PoldiTheme.outline(g, x - tw / 2 - 4, y - 6, tw + 8, 12, PoldiTheme.alpha(PoldiTheme.PRIMARY, 0xB0));
                PoldiTheme.text(g, f, t, x - tw / 2, y - 4, PoldiTheme.TEXT);
            }
        }
        if (cfg.lightOverlay) PoldiSurvival.lightOverlay(g, mc);
        if (cfg.blockHighlight && mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = bhr.getBlockPos();
            var shape = mc.level.getBlockState(bp).getShape(mc.level, bp);
            if (!shape.isEmpty()) box(g, shape.bounds().move(bp.getX(), bp.getY(), bp.getZ()).inflate(0.002), PoldiTheme.PRIMARY, 2);
        }
        if (cfg.itemGlow && mc.player != null) {
            int n = 0;
            Vec3 eye = mc.player.getEyePosition(delta);
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ItemEntity)) continue;
                if (e.distanceToSqr(eye) > 48 * 48) continue;
                box(g, e.getBoundingBox().inflate(0.05), PoldiTheme.SECOND, 1);
                if (++n > 60) break;
            }
        }
    }
}
