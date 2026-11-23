package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * FPS-Boost ueber normale Spieloptionen. Optionen werden per Namen gesucht, damit es auch nach
 * Umbenennungen nicht abstuerzt - fehlt eine Option, wird sie einfach uebersprungen.
 */
public final class PoldiPerf {
    private PoldiPerf() {}

    @SuppressWarnings("unchecked")
    private static OptionInstance<Object> opt(Options o, String names) {
        Object v = PoldiR.call(o, names);
        if (v == null) v = PoldiR.field(o, names);
        return v instanceof OptionInstance<?> oi ? (OptionInstance<Object>) oi : null;
    }

    private static Object enumAt(Object any, int index) {
        Object[] all = ((Enum<?>) any).getDeclaringClass().getEnumConstants();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }

    /** Merkt den alten Wert (als Text) und setzt den neuen. */
    private static void apply(PoldiConfig c, boolean on, String key, OptionInstance<Object> o, Object newVal) {
        if (o == null) return;
        try {
            Object cur = o.get();
            if (on) {
                if (!c.orig.containsKey(key)) c.orig.put(key, cur instanceof Enum<?> e ? "#" + e.ordinal() : String.valueOf(cur));
                o.set(newVal);
            } else if (c.orig.containsKey(key)) {
                String s = c.orig.remove(key);
                Object back;
                if (s.startsWith("#") && cur instanceof Enum<?>) back = enumAt(cur, Integer.parseInt(s.substring(1)));
                else if (cur instanceof Integer) back = Integer.parseInt(s);
                else if (cur instanceof Double) back = Double.parseDouble(s);
                else if (cur instanceof Boolean) back = Boolean.parseBoolean(s);
                else back = null;
                if (back != null) o.set(back);
            }
        } catch (Throwable ignored) {}
    }

    public static void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;
        PoldiConfig c = PoldiConfig.get();

        var particles = opt(o, "particles");
        if (particles != null && particles.get() instanceof Enum<?>) apply(c, c.perfParticles, "particles", particles, enumAt(particles.get(), Integer.MAX_VALUE));
        apply(c, c.perfShadows, "shadows", opt(o, "entityShadows"), false);
        var view = opt(o, "renderDistance");
        if (view != null && view.get() instanceof Integer v) apply(c, c.perfDistance, "view", view, Math.min(v, 8));
        var sim = opt(o, "simulationDistance");
        if (sim != null && sim.get() instanceof Integer v) apply(c, c.perfDistance, "sim", sim, Math.min(v, 6));
        apply(c, c.perfDistance, "scaling", opt(o, "entityDistanceScaling"), 0.5);
        var clouds = opt(o, "cloudStatus");
        if (clouds != null && clouds.get() instanceof Enum<?>) apply(c, c.perfClouds, "clouds", clouds, enumAt(clouds.get(), 0));
        apply(c, c.perfBlend, "blend", opt(o, "biomeBlendRadius"), 0);
        apply(c, c.perfAo, "ao", opt(o, "ambientOcclusion"), false);
        apply(c, c.perfFps, "maxfps", opt(o, "framerateLimit"), 260);
        apply(c, c.perfFps, "vsync", opt(o, "enableVsync"), false);
        c.save();
        try { o.save(); } catch (Throwable ignored) {}
    }
}
