package dev.poldi.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Einstiegspunkt des Poldi Clients: Tasten, HUD, Befehle, Wasserzeichen. */
public class PoldiClient implements ClientModInitializer {
    public static final String ID = "poldi";
    public static final String NAME = "Poldi Client";
    public static final String VERSION = PoldiUpdater.currentVersion();
    public static KeyMapping openMenu, calcKey, macroKey, hudEditKey, glassKey, waypointKey;
    private static int pendingScreen = 0;

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }

    @Override
    public void onInitializeClient() {
        PoldiTheme.reload(PoldiConfig.get());
        PoldiGlass.register();
        PoldiTitle.register();
        PoldiReconnect.register();
        PoldiPvp.register();
        PoldiCursor.register();
        PoldiSurvival.register();
        PoldiScreens.register();

        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD,
            orig -> (g, tick) -> PoldiScoreboard.render(g, tick, () -> orig.extractRenderState(g, tick)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("hud"), PoldiHud::render);

        KeyMapping.Category cat = KeyMapping.Category.register(id("main"));
        openMenu = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT, cat);
        calcKey = key("calc", GLFW.GLFW_KEY_UNKNOWN, cat);
        macroKey = key("macro", GLFW.GLFW_KEY_UNKNOWN, cat);
        hudEditKey = key("hudedit", GLFW.GLFW_KEY_UNKNOWN, cat);
        glassKey = key("glass", GLFW.GLFW_KEY_UNKNOWN, cat);
        waypointKey = key("waypoint", GLFW.GLFW_KEY_UNKNOWN, cat);
        PoldiMisc.zoomKey = key("zoom", GLFW.GLFW_KEY_C, cat);
        PoldiMisc.fullbrightKey = key("fullbright", GLFW.GLFW_KEY_UNKNOWN, cat);
        PoldiMisc.autoClickKey = key("autoclick", GLFW.GLFW_KEY_UNKNOWN, cat);

        ClientLifecycleEvents.CLIENT_STARTED.register(c -> {
            PoldiPerf.apply();
            PoldiMisc.registerSound();
            PoldiGlass.syncButtons();
            PoldiUpdater.checkAsync(false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Screen cur = mc.gui.screen();
            while (openMenu.consumeClick()) if (cur == null) setScreen(new PoldiMenu(null));
            while (calcKey.consumeClick()) if (cur == null) setScreen(new PoldiCalculatorScreen(null));
            while (macroKey.consumeClick()) if (cur == null) PoldiMacro.toggle();
            while (hudEditKey.consumeClick()) if (cur == null) setScreen(new PoldiHudEditor(null));
            while (glassKey.consumeClick()) if (cur == null) { PoldiGlass.toggle(); chat("Unsichtbares Glas wird umgeschaltet ..."); }
            while (waypointKey.consumeClick()) if (cur == null && mc.player != null) {
                String n = "WP" + (PoldiConfig.get().waypoints.size() + 1);
                chat(PoldiWaypoints.addHere(n) ? "Wegpunkt \"" + n + "\" gesetzt." : "Nicht möglich.");
            }
            long q0 = PoldiProf.begin();
            PoldiMacro.tick(mc);
            PoldiProf.end("Tick Sell-Makro", q0);
            q0 = PoldiProf.begin();
            PoldiMisc.tick(mc);
            PoldiProf.end("Tick Misc", q0);
            q0 = PoldiProf.begin();
            PoldiEvents.tick();
            PoldiSurvival.tick(mc);
            PoldiProf.end("Tick Events+Survival", q0);
            int p = pendingScreen;
            pendingScreen = 0;
            switch (p) {
                case 1 -> setScreen(new PoldiMenu(null));
                case 2 -> setScreen(new PoldiCalculatorScreen(null));
                case 3 -> setScreen(new PoldiSellScreen(null));
                case 4 -> setScreen(new PoldiHudEditor(null));
                case 5 -> setScreen(new PoldiKeysScreen(null));
                case 6 -> setScreen(new PoldiTrailerScreen(null));
                default -> {}
            }
        });
        PoldiMisc.registerEvents();
        PoldiTwitch.start();
        PoldiYoutube.start();

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> { if (!overlay) PoldiEvents.onChatLine(msg.getString()); });

        // Wasserzeichen in allen Menues (oben rechts, damit der Chat unten links frei bleibt)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                if (!PoldiConfig.get().watermark || s instanceof PoldiTrailerScreen) return;
                var f = client.font;
                int lx = s.width - f.width(NAME) - 5;
                PoldiLogo.draw(g, lx - 13, 4, 11);
                PoldiTheme.gradientText(g, f, NAME, lx, 5);
            }));

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("note")
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        PoldiConfig cf = PoldiConfig.get(); cf.notes.add(StringArgumentType.getString(c, "text").trim()); cf.save();
                        chat("Notiz " + cf.notes.size() + " gespeichert."); return 1;
                    })))
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("nr", IntegerArgumentType.integer(1)).executes(c -> {
                        PoldiConfig cf = PoldiConfig.get(); int i = IntegerArgumentType.getInteger(c, "nr") - 1;
                        if (i >= cf.notes.size()) { chat("Keine Notiz " + (i + 1) + "."); return 0; }
                        cf.notes.remove(i); cf.save(); chat("Notiz gelöscht."); return 1;
                    })))
                .then(ClientCommands.literal("clear").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get(); cf.notes.clear(); cf.save(); chat("Alle Notizen gelöscht."); return 1;
                }))
                .then(ClientCommands.literal("list").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get();
                    if (cf.notes.isEmpty()) { chat("Keine Notizen."); return 1; }
                    for (int i = 0; i < cf.notes.size(); i++) chatLong((i + 1) + ". " + cf.notes.get(i));
                    return 1;
                })));
            d.register(ClientCommands.literal("profile")
                .then(ClientCommands.literal("list").executes(c -> {
                    var l = PoldiProfiles.list();
                    chatLong(l.isEmpty() ? "Keine Profile gespeichert." : "Profile: " + String.join(", ", l)); return 1;
                }))
                .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    chat(PoldiProfiles.save(n) ? "Profil \"" + PoldiProfiles.clean(n) + "\" gespeichert." : "Speichern fehlgeschlagen."); return 1;
                })))
                .then(ClientCommands.literal("load").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    chat(PoldiProfiles.load(n) ? "Profil \"" + PoldiProfiles.clean(n) + "\" geladen." : "Profil nicht gefunden."); return 1;
                })))
                .then(ClientCommands.literal("delete").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    chat(PoldiProfiles.delete(StringArgumentType.getString(c, "name")) ? "Profil gelöscht." : "Profil nicht gefunden."); return 1;
                }))));
            d.register(ClientCommands.literal("waypoint")
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "name");
                            chat(PoldiWaypoints.addHere(n) ? "Wegpunkt \"" + n + "\" gesetzt." : "Nicht möglich."); return 1;
                        })
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer()).executes(c -> {
                                    String n = StringArgumentType.getString(c, "name");
                                    boolean ok = PoldiWaypoints.add(n, IntegerArgumentType.getInteger(c, "x"),
                                        IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"));
                                    chat(ok ? "Wegpunkt \"" + n + "\" gesetzt." : "Nicht möglich."); return 1;
                                }))))))
                .then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    chat(PoldiWaypoints.remove(StringArgumentType.getString(c, "name")) ? "Wegpunkt gelöscht." : "Wegpunkt nicht gefunden."); return 1;
                })))
                .then(ClientCommands.literal("clear").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get(); cf.waypoints.clear(); cf.save(); chat("Alle Wegpunkte gelöscht."); return 1;
                }))
                .then(ClientCommands.literal("list").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get();
                    if (cf.waypoints.isEmpty()) { chat("Keine Wegpunkte."); return 1; }
                    for (var w : cf.waypoints) chatLong(w.name + ": " + w.x + " " + w.y + " " + w.z + " (" + w.dim + ")");
                    return 1;
                })));
            d.register(ClientCommands.literal("fullbright").executes(c -> {
                PoldiConfig cf = PoldiConfig.get(); cf.fullbright = !cf.fullbright; cf.save();
                chat("Fullbright " + (cf.fullbright ? "an" : "aus")); return 1;
            }));
            d.register(ClientCommands.literal("twitch")
                .then(ClientCommands.literal("off").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get(); cf.twitch = false; cf.save(); chat("Twitch-Chat aus."); return 1;
                }))
                .then(ClientCommands.argument("kanal", StringArgumentType.word()).executes(c -> {
                    PoldiConfig cf = PoldiConfig.get(); cf.twitchChannel = StringArgumentType.getString(c, "kanal"); cf.twitch = true; cf.save();
                    chat("Twitch-Chat: " + cf.twitchChannel); return 1;
                })));
            d.register(ClientCommands.literal("youtube")
                .then(ClientCommands.literal("off").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get(); cf.youtube = false; cf.save(); chat("YouTube-Chat aus."); return 1;
                }))
                .then(ClientCommands.argument("video", StringArgumentType.greedyString()).executes(c -> {
                    PoldiConfig cf = PoldiConfig.get(); cf.youtubeVideo = StringArgumentType.getString(c, "video").trim(); cf.youtube = true; cf.save();
                    chat(cf.youtubeKey.isBlank() ? "Video gesetzt – API-Key fehlt (Menü → Streaming → YouTube)." : "YouTube-Chat: " + PoldiYoutube.videoId(cf.youtubeVideo)); return 1;
                })));
            d.register(ClientCommands.literal("giveaway")
                .then(ClientCommands.literal("start")
                    .then(ClientCommands.argument("sekunden", IntegerArgumentType.integer(5, 3600))
                        .then(ClientCommands.argument("preis", StringArgumentType.greedyString()).executes(c -> {
                            PoldiEvents.startGiveaway(IntegerArgumentType.getInteger(c, "sekunden"), StringArgumentType.getString(c, "preis"));
                            return 1;
                        }))))
                .then(ClientCommands.literal("end").executes(c -> { PoldiEvents.drawWinner(); return 1; }))
                .then(ClientCommands.literal("cancel").executes(c -> { PoldiEvents.cancelGiveaway(); return 1; })));
            d.register(ClientCommands.literal("stopwatch")
                .executes(c -> { chat(PoldiEvents.stopwatch(PoldiEvents.swRunning() ? "stop" : "start")); return 1; })
                .then(ClientCommands.literal("start").executes(c -> { chat(PoldiEvents.stopwatch("start")); return 1; }))
                .then(ClientCommands.literal("stop").executes(c -> { chat(PoldiEvents.stopwatch("stop")); return 1; }))
                .then(ClientCommands.literal("lap").executes(c -> { chat(PoldiEvents.stopwatch("lap")); return 1; }))
                .then(ClientCommands.literal("reset").executes(c -> { chat(PoldiEvents.stopwatch("reset")); return 1; })));
            d.register(ClientCommands.literal("timer")
                .then(ClientCommands.literal("cancel").executes(c -> { chat(PoldiEvents.cancelTimers() + " Timer gelöscht."); return 1; }))
                .then(ClientCommands.argument("sekunden", IntegerArgumentType.integer(1, 86400))
                    .executes(c -> { int s = IntegerArgumentType.getInteger(c, "sekunden"); PoldiEvents.addTimer(s, s + " s"); chat("Timer gestartet: " + s + " s"); return 1; })
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> { int s = IntegerArgumentType.getInteger(c, "sekunden"); String n = StringArgumentType.getString(c, "name"); PoldiEvents.addTimer(s, n); chat("Timer \"" + n + "\": " + s + " s"); return 1; }))));
            d.register(ClientCommands.literal("calc").executes(c -> { pendingScreen = 2; return 1; }));
            d.register(ClientCommands.literal("glass").executes(c -> {
                boolean ok = PoldiGlass.toggle();
                chat(ok ? "Unsichtbares Glas wird umgeschaltet ..." : "Glas-Pack nicht gefunden.");
                return ok ? 1 : 0;
            }));
            d.register(ClientCommands.literal("poldi")
                .then(ClientCommands.literal("menu").executes(c -> { pendingScreen = 1; return 1; }))
                .then(ClientCommands.literal("lag").executes(c -> { for (String ln : PoldiProf.analyze()) chat(ln); return 1; }))
                .then(ClientCommands.literal("perf")
                    .executes(c -> { for (String ln : PoldiProf.report()) chat(ln); return 1; })
                    .then(ClientCommands.literal("reset").executes(c -> { PoldiProf.reset(); chat("Messung neu gestartet."); return 1; })))
                .then(ClientCommands.literal("lite").executes(c -> {
                    PoldiConfig cf = PoldiConfig.get();
                    cf.keystrokes = false; cf.coords = false; cf.moduleList = false; cf.watermark = false;
                    cf.lightOverlay = false; cf.itemGlow = false; cf.blockHighlight = false;
                    cf.save(); chat("Lite-Modus: HUDs und Overlays aus."); return 1;
                }))
                .then(ClientCommands.literal("sell").executes(c -> { pendingScreen = 3; return 1; }))
                .then(ClientCommands.literal("hud").executes(c -> { pendingScreen = 4; return 1; }))
                .then(ClientCommands.literal("keys").executes(c -> { pendingScreen = 5; return 1; }))
                .then(ClientCommands.literal("trailer").executes(c -> { pendingScreen = 6; return 1; }))
                .then(ClientCommands.literal("update").executes(c -> {
                    chat("Suche Update … (aktuell " + PoldiUpdater.currentVersion() + ")");
                    PoldiUpdater.checkAsync(true); return 1;
                }))
                .then(ClientCommands.literal("leak")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        String w = PoldiMisc.leakReason(StringArgumentType.getString(c, "text"));
                        chatLong("Anti-Leak (" + (PoldiConfig.get().antiLeak ? "an" : "AUS") + "): "
                            + (w == null ? "würde durchgehen" : "würde blockieren – " + w));
                        return 1;
                    })))
                .then(ClientCommands.literal("scale")
                    .then(ClientCommands.argument("wert", FloatArgumentType.floatArg(0.5f, 3f)).executes(c -> {
                        PoldiConfig cfg = PoldiConfig.get(); cfg.hudScale = FloatArgumentType.getFloat(c, "wert"); cfg.save();
                        chat("HUD-Größe " + cfg.hudScale); return 1;
                    }))));
        });
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category cat) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + ID + "." + name, InputConstants.Type.KEYSYM, code, cat));
    }

    public static void setScreen(Screen s) { Minecraft.getInstance().gui.setScreen(s); }

    private static void sys(Component c) {
        Minecraft mc = Minecraft.getInstance();
        try { mc.gui.hud.getChat().addClientSystemMessage(c); } catch (Throwable ignored) {}
    }

    /** Immer als Chat-Nachricht (nicht als Toast), z. B. fuer lange Texte. */
    public static void chatLong(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || text == null) return;
        sys(Component.literal("[Poldi] ").withStyle(ChatFormatting.RED)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    public static void chat(String text) {
        if (text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (PoldiConfig.get().notify && text.length() <= 70) {
            PoldiNotify.push(NAME, text, PoldiTheme.PRIMARY);
            return;
        }
        chatLong(text);
    }

    /** Spielt einen Vanilla-Sound im GUI (z. B. "block.anvil.land"). */
    public static void sound(String id, float vol, float pitch) {
        try {
            var ev = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
            if (ev != null) Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, pitch, vol));
        } catch (Throwable ignored) {}
    }

    public static void sound(String id) { sound(id, 1f, 1f); }

    /** 1500 -> 1.5k */
    public static String fmt(long v) {
        double a = Math.abs((double) v);
        if (a >= 1e12) return trim(v / 1e12) + "T";
        if (a >= 1e9) return trim(v / 1e9) + "B";
        if (a >= 1e6) return trim(v / 1e6) + "M";
        if (a >= 1e3) return trim(v / 1e3) + "k";
        return Long.toString(v);
    }

    private static String trim(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
