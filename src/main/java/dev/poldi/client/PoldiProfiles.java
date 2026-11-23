package dev.poldi.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Profile: speichert ALLE Einstellungen (inkl. HUD-Positionen, Farben, Notizen, Wegpunkte, Tasten) unter einem Namen. */
public final class PoldiProfiles {
    private PoldiProfiles() {}

    private static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("poldi-profiles");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    public static String clean(String n) {
        String s = n == null ? "" : n.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    public static List<String> list() {
        List<String> r = new ArrayList<>();
        try (Stream<Path> st = Files.list(dir())) {
            st.forEach(p -> {
                String f = p.getFileName().toString();
                if (f.endsWith(".json")) r.add(f.substring(0, f.length() - 5));
            });
        } catch (Exception ignored) {}
        r.sort(String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    public static boolean save(String name) {
        String n = clean(name);
        if (n.isEmpty()) return false;
        PoldiConfig c = PoldiConfig.get();
        c.keys.clear();
        for (KeyMapping k : PoldiKeysScreen.binds()) if (k != null) c.keys.put(k.getName(), k.saveString());
        try {
            Files.writeString(dir().resolve(n + ".json"), c.toJson());
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean load(String name) {
        String n = clean(name);
        Path f = dir().resolve(n + ".json");
        if (n.isEmpty() || !Files.exists(f)) return false;
        try {
            PoldiConfig nc = PoldiConfig.parse(Files.readString(f));
            if (nc == null) return false;
            nc.orig = PoldiConfig.get().orig; // gemerkte Original-Spieloptionen behalten
            PoldiConfig.replace(nc);
            for (KeyMapping k : PoldiKeysScreen.binds()) {
                if (k == null) continue;
                String v = nc.keys.get(k.getName());
                if (v != null) k.setKey(InputConstants.getKey(v));
            }
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            PoldiTheme.reload(nc);
            PoldiPerf.apply();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean delete(String name) {
        try { return Files.deleteIfExists(dir().resolve(clean(name) + ".json")); } catch (Exception e) { return false; }
    }
}
