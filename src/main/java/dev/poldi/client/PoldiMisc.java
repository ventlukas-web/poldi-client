package dev.poldi.client;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fullbright, Autoclicker, Zoom, Auto-Sprint, Totem-Sounds, Anti-Leak, Streamer-Name. */
public final class PoldiMisc {
    private PoldiMisc() {}

    public static KeyMapping zoomKey, fullbrightKey, autoClickKey;

    public static final String[][] TOTEM = {
        {"Aus", ""}, {"Amboss", "block.anvil.land"}, {"Glocke", "block.bell.use"}, {"Ding", "entity.experience_orb.pickup"},
        {"Glas", "block.glass.break"}, {"Donner", "entity.lightning_bolt.thunder"}, {"Drache", "entity.ender_dragon.growl"},
        {"Amethyst", "block.amethyst_block.chime"}, {"Feuerwerk", "entity.firework_rocket.blast"},
        {"Wither", "entity.wither.shoot"}, {"Villager", "entity.villager.yes"}, {"Explosion", "entity.generic.explode"}
    };

    private static boolean fbApplied, zooming;
    private static int zoomOrig;
    private static double acc;
    public static int combo, maxCombo, hits;
    public static long lastHit;

    public static void playTotem(int idx) {
        if (idx <= 0 || idx >= TOTEM.length) return;
        PoldiClient.sound(TOTEM[idx][1]);
    }

    /** Totem-Pop erkennen: Sound-Listener (wie bei den Untertiteln). Per Reflection, damit Signatur-Aenderungen nicht abstuerzen. */
    public static void registerSound() {
        try {
            Class<?> l = Class.forName("net.minecraft.client.sounds.SoundEventListener");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(l.getClassLoader(), new Class<?>[]{l}, (p, m, args) -> {
                if (m.getDeclaringClass() == Object.class) {
                    return switch (m.getName()) {
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == args[0];
                        default -> "PoldiTotemListener";
                    };
                }
                if (args != null && args.length > 0 && args[0] != null) {
                    Object id = PoldiR.get(args[0], "getIdentifier|getLocation|identifier");
                    int idx = PoldiConfig.get().totemIdx;
                    if (idx > 0 && id != null && id.toString().endsWith("item.totem.use")) Minecraft.getInstance().execute(() -> playTotem(idx));
                }
                return null;
            });
            PoldiR.call(Minecraft.getInstance().getSoundManager(), "addListener", proxy);
        } catch (Throwable ignored) {}
    }

    private static Component alias(Component msg) {
        PoldiConfig cfg = PoldiConfig.get();
        if (!cfg.streamer || cfg.streamerName == null || cfg.streamerName.isBlank()) return msg;
        Minecraft mc = Minecraft.getInstance();
        String real = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
        if (real == null || real.isBlank() || !msg.getString().toLowerCase().contains(real.toLowerCase())) return msg;
        Pattern pat = Pattern.compile(Pattern.quote(real), Pattern.CASE_INSENSITIVE);
        MutableComponent out = Component.empty();
        msg.visit((style, str) -> {
            out.append(Component.literal(pat.matcher(str).replaceAll(Matcher.quoteReplacement(cfg.streamerName))).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static void registerEvents() {
        ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> alias(msg));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (entity instanceof LivingEntity && level.isClientSide()) {
                long now = System.currentTimeMillis();
                if (now - lastHit > 2500) combo = 0;
                combo++; hits++; lastHit = now;
                maxCombo = Math.max(maxCombo, combo);
            }
            return InteractionResult.PASS;
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(PoldiMisc::allowOut);
        ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> allowOut("/" + cmd));
    }

    public static void tick(Minecraft mc) {
        PoldiConfig cfg = PoldiConfig.get();
        autoClick(mc, cfg);
        if (mc.player == null || mc.level == null) { fbApplied = false; return; }

        while (fullbrightKey != null && fullbrightKey.consumeClick()) {
            cfg.fullbright = !cfg.fullbright; cfg.save();
            PoldiClient.chat("Fullbright " + (cfg.fullbright ? "an" : "aus"));
        }
        while (autoClickKey != null && autoClickKey.consumeClick()) {
            cfg.autoClick = !cfg.autoClick; cfg.save();
            PoldiClient.chat("Autoclicker " + (cfg.autoClick ? "an" : "aus"));
        }
        boolean noScreen = mc.gui.screen() == null;

        if (cfg.autoSprint && noScreen && mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.isUsingItem())
            mc.player.setSprinting(true);

        // Zoom (Taste halten)
        boolean zk = zoomKey != null && zoomKey.isDown() && noScreen;
        if (zk && !zooming) { zoomOrig = mc.options.fov().get(); mc.options.fov().set(30); zooming = true; }
        else if (!zk && zooming) { mc.options.fov().set(zoomOrig); zooming = false; }

        // Fullbright = clientseitiger Nachtsicht-Effekt
        if (cfg.fullbright) {
            MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 400) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false, false));
                fbApplied = true;
            }
        } else if (fbApplied) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            fbApplied = false;
        }
    }

    /** Autoclicker: echte Mausklicks mit eingestellter CPS - im Spiel, in Inventaren und GUIs, aber nie im ESC-Menue. */
    private static void autoClick(Minecraft mc, PoldiConfig cfg) {
        var scr = mc.gui.screen();
        boolean skip = !cfg.autoClick || PoldiMacro.isRunning() || scr instanceof PoldiScreen || scr instanceof PauseScreen
            || scr instanceof PoldiTrailerScreen;
        if (skip) { acc = 0; return; }
        acc += Math.max(1, Math.min(40, cfg.autoCps)) / 20.0;
        int n = Math.min(4, (int) acc);
        acc -= (int) acc;
        long win = mc.getWindow().handle();
        int btn = cfg.autoRight ? 1 : 0;
        for (int i = 0; i < n; i++) guiClick(win, btn);
    }

    private static GLFWMouseButtonCallback mouseCb;

    /** Synthetischer Klick (Druecken + Loslassen) ueber den echten Maus-Callback des Spiels. */
    private static void guiClick(long win, int btn) {
        if (mouseCb == null) {
            mouseCb = GLFW.glfwSetMouseButtonCallback(win, null);
            GLFW.glfwSetMouseButtonCallback(win, mouseCb);
        }
        if (mouseCb == null) return;
        mouseCb.invoke(win, btn, GLFW.GLFW_PRESS, 0);
        mouseCb.invoke(win, btn, GLFW.GLFW_RELEASE, 0);
    }

    // ---------- Anti-Leak ----------

    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern NUM = Pattern.compile("-?\\d{2,6}");
    private static final Pattern DM = Pattern.compile("^/(msg|w|tell|r|reply|m|whisper|t)\\b.*", Pattern.CASE_INSENSITIVE);

    /** @return Grund, warum die Nachricht gesperrt wuerde, sonst null */
    public static String leakReason(String text) {
        PoldiConfig cfg = PoldiConfig.get();
        Minecraft mc = Minecraft.getInstance();
        String low = text.toLowerCase();
        for (String w : cfg.leakWords.split(",")) {
            w = w.trim().toLowerCase();
            if (w.length() >= 2 && low.contains(w)) return "gesperrtes Wort \"" + w + "\"";
        }
        String user = System.getProperty("user.name", "");
        if (user.length() >= 4 && low.contains(user.toLowerCase())) return "dein PC-Benutzername";
        if (IP.matcher(text).find()) return "IP-Adresse";
        boolean chatLike = !text.startsWith("/") || DM.matcher(text).matches();
        if (chatLike && mc.player != null) {
            Matcher m = NUM.matcher(text);
            double px = mc.player.getX(), pz = mc.player.getZ();
            java.util.List<Integer> nums = new java.util.ArrayList<>();
            while (m.find()) { try { nums.add(Integer.parseInt(m.group())); } catch (Exception ignored) {} }
            for (int a : nums) {
                if (Math.abs(a) >= 50 && (Math.abs(a - px) <= 4 || Math.abs(a - pz) <= 4)) return "deine Koordinaten";
                for (int b : nums) if (Math.abs(a - px) < 150 && Math.abs(b - pz) < 150) return "deine Koordinaten (Gegend)";
            }
        }
        return null;
    }

    private static boolean allowOut(String text) {
        if (!PoldiConfig.get().antiLeak) return true;
        String why = leakReason(text);
        if (why == null) return true;
        PoldiNotify.push("Anti-Leak", "Blockiert: " + why, PoldiTheme.BAD);
        PoldiClient.chatLong("Anti-Leak hat die Nachricht blockiert (" + why + ").");
        return false;
    }
}
