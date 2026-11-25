package dev.poldi.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PoldiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("poldi.json");
    private static PoldiConfig instance;

    // Anzeige
    public boolean sound = true;
    public boolean watermark = true;
    public boolean niceInventory = false;
    public boolean niceButtons = false;
    /** 2 = Vanilla-Design (alte rote Einstellungen werden einmalig umgestellt). */
    public int uiVersion = 0;
    public boolean menuBackground = false;
    /** Neue Versionen automatisch von GitHub laden. */
    public boolean autoUpdate = true;
    /** Trailer mit Musik beim ersten Spielstart, ueberspringbar. */
    public boolean introTrailer = true;
    public boolean introSeen = false;
    public String themeBase = "#0C0505";
    public int cfgVersion = 1;

    // Sell-Makro
    public String sellCommand = "/sell";
    public java.util.List<String> sellItems = new java.util.ArrayList<>();
    public int sellClickDelay = 2;
    public String sellTitleFilter = "";
    public boolean sellAuto = false;
    public int sellIntervalMs = 10000;
    public boolean sellShiftAll = true;
    public boolean sellAlwaysAll = true;

    // HUD
    public boolean showFps = false;
    public boolean keystrokes = true;
    public boolean coords = true;
    public boolean ping = false;
    public boolean speed = false;
    public boolean clock = false;
    public boolean moduleList = true;
    public float hudScale = 1.0f;
    public java.util.Map<String, float[]> hudPos = new java.util.HashMap<>();
    public boolean notesHud = false;

    // Wegpunkte + Tasten-Snapshot fuer Profile
    public static class Wp { public String name = "", dim = ""; public int x, y, z; }
    public boolean wpOn = true;
    public java.util.List<Wp> waypoints = new java.util.ArrayList<>();
    public java.util.Map<String, String> keys = new java.util.HashMap<>();

    // Scoreboard
    public boolean sbCustom = false, sbNumbers = false, sbBorderOn = true;
    public boolean sbTheme = true;
    public int sbAlpha = 170;
    public float sbScale = 1.0f;
    public String sbBg = "#140606", sbBorder = "#DC2626", sbText = "#FFFFFF";

    // Streamer-Modus
    public boolean streamer = false, streamerHideXyz = true;
    public String streamerName = "Poldi";
    public boolean customHud = false;
    public String customTitle = "Mein HUD";
    public java.util.List<String> customLines = new java.util.ArrayList<>(java.util.List.of("XYZ: {x} {y} {z}", "FPS: {fps}", "Tag: {tag}"));
    public boolean nearbyHud = false, targetHud = false, comboHud = false, memHud = false, vitalsHud = false;
    public boolean compassHud = false, sellHud = false, armorWarn = true;
    public int armorWarnPct = 10;
    public boolean armorHud = false, potionHud = false, sessionHud = false, autoSprint = false;
    public java.util.List<String> notes = new java.util.ArrayList<>();
    public boolean notify = true;
    public boolean fullbright = false;
    public boolean itemGlow = false;
    public boolean blockHighlight = false;
    public boolean noFog = false;
    public int cursorStyle = 0, cursorSize = 0;

    // PvP (nur Anzeigen)
    public boolean cpsHud = false, reachHud = false, pvpItems = false;
    public int hitSoundIdx = 0;
    public boolean hitMarker = false, hitSound = false, lowHpWarn = false, cooldownBar = false;
    public boolean satBorder = true;

    // Komfort
    public boolean autoReconnect = true;
    public int reconnectSec = 5;
    public boolean ownNametag = false;
    public boolean antiLeak = false;
    public boolean joinButton = true;
    public String serverName = "";
    public String serverIp = "";
    public String leakWords = "";
    public boolean autoClick = false;
    public int autoCps = 10;
    public boolean autoRight = false;
    public int totemIdx = 0;

    // Streaming
    public boolean youtube = false;
    public String youtubeKey = "", youtubeVideo = "";
    public boolean twitch = false;
    public String twitchChannel = "";
    public int twitchLines = 8;
    public boolean giveawayAnnounce = true;
    public String giveawayKeyword = "join";

    // Survival
    public boolean coordsBiome = true;
    public boolean coordsNether = true;
    public boolean deathWaypoint = true;
    public boolean toolWarn = true;
    public int toolWarnPct = 5;
    public boolean hungerWarn = true;
    public int hungerWarnAt = 6;
    public boolean invFullWarn = true;
    public boolean lightOverlay = false;
    public boolean lightHud = false;
    public boolean dayHud = false;
    public boolean pickupLog = true;

    // Design (Standard: Rot)
    public String color1 = "#DC2626";
    public String color2 = "#F87171";
    public int bgOpacity = 208;

    // Performance (alte Werte werden gemerkt, damit man sie zurueckstellen kann)
    public boolean perfParticles = false, perfShadows = false, perfDistance = false;
    public boolean perfClouds = false, perfBlend = false, perfAo = false, perfFps = false;
    public java.util.Map<String, String> orig = new java.util.HashMap<>();

    public static PoldiConfig get() {
        if (instance == null) {
            try {
                if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), PoldiConfig.class);
            } catch (Exception ignored) {}
            if (instance == null) { instance = new PoldiConfig(); instance.save(); }
            fix(instance);
        }
        return instance;
    }

    private static void fix(PoldiConfig c) {
        if (c.uiVersion < 2) { // auf Vanilla-Design umstellen
            c.uiVersion = 2; c.niceButtons = false; c.niceInventory = false; c.menuBackground = false; c.cursorStyle = 0;
            try { c.save(); } catch (Throwable ignored) {}
        }
        if (c.orig == null) c.orig = new java.util.HashMap<>();
        if (c.waypoints == null) c.waypoints = new java.util.ArrayList<>();
        if (c.notes == null) c.notes = new java.util.ArrayList<>();
        if (c.customLines == null) c.customLines = new java.util.ArrayList<>();
        if (c.hudPos == null) c.hudPos = new java.util.HashMap<>();
        if (c.keys == null) c.keys = new java.util.HashMap<>();
        if (c.sellItems == null) c.sellItems = new java.util.ArrayList<>();
        if (c.serverName == null) c.serverName = "";
        if (c.serverIp == null) c.serverIp = "";
    }

    public String toJson() { return GSON.toJson(this); }

    public static PoldiConfig parse(String json) {
        try { return GSON.fromJson(json, PoldiConfig.class); } catch (Exception e) { return null; }
    }

    public static void replace(PoldiConfig c) {
        fix(c);
        instance = c;
        c.save();
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }
}
