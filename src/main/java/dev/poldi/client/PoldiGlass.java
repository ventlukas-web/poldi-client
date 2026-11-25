package dev.poldi.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

/** Eingebaute Resource-Packs: unsichtbares Glas, Anti-Leak-Bloecke und die roten Vanilla-Buttons. */
public final class PoldiGlass {
    private PoldiGlass() {}

    public static void register() {
        FabricLoader.getInstance().getModContainer(PoldiClient.ID).ifPresent(c -> {
            ResourceLoader.registerBuiltinPack(PoldiClient.id("invisible_glass"), c,
                Component.literal("Poldi - Unsichtbares Glas"), PackActivationType.NORMAL);
            ResourceLoader.registerBuiltinPack(PoldiClient.id("ore_cloak"), c,
                Component.literal("Poldi - Anti-Leak Bloecke"), PackActivationType.NORMAL);
            // Rote Vanilla-Buttons und Slider fuer ALLE Menues (Schalter "Button-Design")
            ResourceLoader.registerBuiltinPack(PoldiClient.id("vanilla_buttons"), c,
                Component.literal("Poldi - Rote Vanilla-Buttons"), PackActivationType.NORMAL);
        });
    }

    private static String findId(PackRepository pm, String key) {
        for (Pack p : pm.getAvailablePacks()) if (p.getId().contains(key)) return p.getId();
        return null;
    }

    public static boolean isOn() { return isOn("invisible_glass"); }

    public static boolean isOn(String key) {
        PackRepository pm = Minecraft.getInstance().getResourcePackRepository();
        String id = findId(pm, key);
        return id != null && pm.getSelectedIds().contains(id);
    }

    /** Anti-Leak: Erze und Wertbloecke werden als Netherite-Block dargestellt. */
    public static boolean setCloak(boolean on) { return isOn("ore_cloak") == on || set("ore_cloak", on); }

    public static boolean set(String key, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository pm = mc.getResourcePackRepository();
        String id = findId(pm, key);
        if (id == null) return false;
        List<String> enabled = new ArrayList<>(pm.getSelectedIds());
        if (on) { if (!enabled.contains(id)) enabled.add(id); } else enabled.remove(id);
        pm.setSelected(enabled);
        mc.options.updateResourcePacks(pm);
        return true;
    }

    /** Button-Design an/aus passend zur Einstellung (beim Start und beim Umschalten). */
    public static void syncButtons() {
        try {
            boolean want = PoldiConfig.get().niceButtons;
            if (isOn("vanilla_buttons") != want) set("vanilla_buttons", want);
        } catch (Throwable ignored) {}
    }

    public static boolean toggle() { return set("invisible_glass", !isOn()); }
}
