package dev.poldi.client;

import net.minecraft.client.Minecraft;

/** Wegpunkte: Koordinaten speichern, als Marker in der Welt und als Liste mit Richtungspfeil anzeigen. */
public final class PoldiWaypoints {
    private PoldiWaypoints() {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static String dim(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    public static boolean add(String name, int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return add(name, x, y, z, dim(mc));
    }

    public static boolean add(String name, int x, int y, int z, String dim) {
        PoldiConfig c = PoldiConfig.get();
        c.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        PoldiConfig.Wp w = new PoldiConfig.Wp();
        w.name = name; w.x = x; w.y = y; w.z = z; w.dim = dim;
        c.waypoints.add(w);
        c.save();
        return true;
    }

    public static boolean addHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return add(name, (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
    }

    public static boolean remove(String name) {
        PoldiConfig c = PoldiConfig.get();
        boolean r = c.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        if (r) c.save();
        return r;
    }

    /** Pfeil zum Wegpunkt relativ zur Blickrichtung (oben = geradeaus). */
    public static String arrow(Minecraft mc, PoldiConfig.Wp w) {
        double dx = w.x + 0.5 - mc.player.getX(), dz = w.z + 0.5 - mc.player.getZ();
        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = yawTo - mc.player.getYRot();
        rel = ((rel % 360) + 540) % 360 - 180;
        int idx = (int) Math.round(rel / 45.0);
        return ARROWS[((idx % 8) + 8) % 8];
    }

    public static double dist(Minecraft mc, PoldiConfig.Wp w) {
        double dx = w.x + 0.5 - mc.player.getX(), dy = w.y - mc.player.getY(), dz = w.z + 0.5 - mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
