package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Mod-Menue mit allen Funktionen des Poldi Clients (rotes Vanilla-Design). */
public class PoldiMenu extends PoldiScreen {
    private static final int PW = 300;
    private int panelBottom = 274;
    /** Seiten: Index = Seiten-ID. */
    private static final String[] TABS = {"Allgemein", "-", "HUD", "Design", "Anzeige", "FPS", "Tools", "Sell", "Extras", "Twitch & Events",
        "Notizen", "Mehr HUDs", "Eigenes HUD", "Scoreboard", "Streamer", "Commands", "Profile", "Wegpunkte", "Settings", "YouTube", "PvP",
        "Komfort", "Survival · Warnungen", "Survival · Welt"};
    private static final String[] CATS = {"General", "Survival", "Visuals", "PvP", "Streaming", "Tools", "Commands"};
    private static final int[][] CAT_PAGES = {{0, 3, 18, 16}, {22, 23}, {4, 2, 11, 12, 13, 8}, {20}, {9, 19, 14}, {6, 7, 10, 17, 21, 5}, {15}};

    private static int catOf(int leaf) {
        for (int c = 0; c < CAT_PAGES.length; c++) for (int p : CAT_PAGES[c]) if (p == leaf) return c;
        return 0;
    }

    private static int pageOf(int leaf) {
        int[] ps = CAT_PAGES[catOf(leaf)];
        for (int i = 0; i < ps.length; i++) if (ps[i] == leaf) return i;
        return 0;
    }

    private static int tab = 0;

    public static void openTab(int t) { tab = t; }

    private final Screen parent;

    private record Label(String text, int x, int y, int color) {}
    private final List<Label> labels = new ArrayList<>();
    private int left, top;
    private boolean glassOn;

    public PoldiMenu(Screen parent) {
        super(Component.literal(PoldiClient.NAME));
        this.parent = parent;
    }

    private void reopen() { rebuildWidgets(); }

    @Override
    protected void build() {
        labels.clear();
        left = width / 2 - PW / 2;
        top = Math.max(32, (height - 290) / 2 + 28);

        int tw = PW / 4, cat = catOf(tab), pg = pageOf(tab);
        for (int i = 0; i < CATS.length; i++) {
            final int idx = i;
            button(() -> CATS[idx], () -> { tab = CAT_PAGES[idx][0]; query = ""; scroll = 0; reopen(); },
                left + (i % 4) * tw, top + (i / 4) * 17, tw - 2, 16, cat == i);
        }
        final int[] ps = CAT_PAGES[cat];
        int py = top + 38;
        if (ps.length > 1) {
            button(() -> "<", () -> { tab = ps[(pg + ps.length - 1) % ps.length]; query = ""; scroll = 0; reopen(); }, left, py, 40, 20);
            button(() -> TABS[tab] + " · " + (pageOf(tab) + 1) + "/" + ps.length, () -> {}, left + 44, py, PW - 88, 20, true);
            button(() -> ">", () -> { tab = ps[(pg + 1) % ps.length]; query = ""; scroll = 0; reopen(); }, left + PW - 40, py, 40, 20);
        } else {
            button(() -> TABS[tab], () -> {}, left, py, PW, 20, true);
        }
        int y = top + 88;
        EditBox sf = field(left, top + 62, PW, query, s -> { query = s; scroll = 0; searchFocus = true; reopen(); });
        sf.setHint(Component.literal("Suche … (Einstellungen, Tools, Befehle)"));
        searchField = sf;
        if (searchFocus) { setFocused(sf); sf.setFocused(true); searchFocus = false; }
        int firstChild = children().size();
        if (query.trim().isEmpty()) runTab(tab, y);
        else searchResults(y);
        slots.clear();
        int bottomMost = y;
        for (int i = firstChild; i < children().size(); i++)
            if (children().get(i) instanceof AbstractWidget cw) {
                slots.add(new Slot(cw, cw.getY()));
                bottomMost = Math.max(bottomMost, cw.getY() + cw.getHeight());
            }
        for (Label l : labels) bottomMost = Math.max(bottomMost, l.y() + 10);
        contentH = bottomMost - y + 2;
        int avail = height - 40 - y - 6;
        bandTop = y;
        bandH = Math.max(48, Math.min(contentH, avail));
        int by = y + bandH + 6;
        button(() -> "Tasten …", () -> open(new PoldiKeysScreen(this)), left, by, 96, 20, true);
        button(() -> "Schließen", this::onClose, left + 102, by, 96, 20);
        button(() -> "HUD verschieben", () -> open(new PoldiHudEditor(this)), left + 204, by, 96, 20, true);
        panelBottom = by + 20 + 24 - top;
        applyScroll();
    }

    private EditBox searchField;

    /** Ein Klick in die Suchleiste reicht: sofort fokussieren. */
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        EditBox sf = searchField;
        if (sf != null && sf.visible && sf.isMouseOver(click.x(), click.y())) { setFocused(sf); sf.setFocused(true); }
        return super.mouseClicked(click, doubled);
    }

    private record Slot(AbstractWidget w, int y) {}
    private final List<Slot> slots = new ArrayList<>();
    private static int scroll = 0;
    private int contentH, bandTop, bandH;

    private int maxScroll() { return Math.max(0, contentH - bandH); }

    private void applyScroll() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        for (Slot sl : slots) {
            int ny = sl.y() - scroll;
            sl.w().setY(ny);
            sl.w().visible = ny >= bandTop - 1 && ny + sl.w().getHeight() <= bandTop + bandH + 1;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll() > 0) {
            scroll -= (int) Math.signum(verticalAmount) * 24;
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static String query = "";
    private boolean searchFocus;

    private void runTab(int t, int y) {
        switch (t) {
            case 0 -> generalTab(y);
            case 2 -> hudTab(y);
            case 3 -> designTab(y);
            case 4 -> visualsTab(y);
            case 5 -> perfTab(y);
            case 6 -> toolsTab(y);
            case 7 -> sellTab(y);
            case 8 -> extrasTab(y);
            case 9 -> eventsTab(y);
            case 10 -> notesTab(y);
            case 11 -> moreHudTab(y);
            case 12 -> customTab(y);
            case 13 -> scoreboardTab(y);
            case 14 -> streamerTab(y);
            case 15 -> commandsTab(y);
            case 16 -> profileTab(y);
            case 17 -> waypointTab(y);
            case 18 -> settingsTab(y);
            case 19 -> youtubeTab(y);
            case 21 -> comfortTab(y);
            case 22 -> survivalWarnTab(y);
            case 23 -> survivalWorldTab(y);
            default -> pvpTab(y);
        }
    }

    /** Baut jede Seite testweise auf, behaelt nur Buttons, die zur Suche passen. */
    private void searchResults(int y0) {
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<PoldiButton> hits = new ArrayList<>();
        List<String> from = new ArrayList<>();
        int saveTab = tab;
        for (int t = 0; t < TABS.length; t++) {
            if (TABS[t].equals("-")) continue;
            int b0 = btns.size(), c0 = children().size(), l0 = labels.size();
            tab = t;
            try { runTab(t, y0); } catch (Throwable ex) { /* Seite nicht durchsuchbar */ }
            List<PoldiButton> added = new ArrayList<>(btns.subList(b0, btns.size()));
            List<GuiEventListener> kids = new ArrayList<>(children().subList(c0, children().size()));
            for (var e : kids) removeWidget(e);
            while (btns.size() > b0) btns.remove(btns.size() - 1);
            while (labels.size() > l0) labels.remove(labels.size() - 1);
            for (PoldiButton b : added) {
                String lab = b.label();
                if (!lab.isEmpty() && lab.toLowerCase(java.util.Locale.ROOT).contains(q)) { hits.add(b); from.add(CATS[catOf(t)] + " › " + TABS[t]); }
            }
        }
        tab = saveTab;
        int y = y0;
        for (int t = 0; t < TABS.length && y < y0 + 8 * 22; t++) {
            if (TABS[t].equals("-")) continue;
            String full = CATS[catOf(t)] + " " + TABS[t];
            if (!full.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            final int idx = t;
            button(() -> "Öffnen: " + CATS[catOf(idx)] + " › " + TABS[idx], () -> { tab = idx; query = ""; scroll = 0; reopen(); }, left, y, PW, 20, true);
            y += 22;
        }
        int shown = 0;
        for (int k = 0; k < hits.size() && shown < 8; k++) {
            PoldiButton b = hits.get(k);
            String tn = from.get(k);
            b.setX(left); b.setY(y); b.setWidth(PW);
            PoldiButton nb = button(() -> tn + " › " + b.label(), () -> b.onPress(null), left, y, PW, 20, b.accent);
            nb.active = b.active;
            y += 22; shown++;
        }
        if (shown == 0 && y == y0) labels.add(new Label("Nichts gefunden für „" + query.trim() + "“", left, y0 + 4, PoldiTheme.MUTED));
        else if (hits.size() > shown) labels.add(new Label("… " + (hits.size() - shown) + " weitere – Suche eingrenzen", left, y + 2, PoldiTheme.MUTED));
    }

    // ---------- General ----------

    private void generalTab(int y) {
        PoldiConfig c = c();
        button(() -> "Trailer ansehen", () -> open(new PoldiTrailerScreen(this)), left, y, 146, 20, true);
        button(() -> "Rechner öffnen", () -> open(new PoldiCalculatorScreen(this)), left + 154, y, 146, 20, true);
        y += 24;
        button(() -> "HUD verschieben", () -> open(new PoldiHudEditor(this)), left, y, 146, 20, true);
        toggle("Modul-Liste", () -> c.moduleList, v -> { c.moduleList = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "HUD kleiner", () -> { c.hudScale = Math.max(0.5f, Math.round((c.hudScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "HUD größer (" + c.hudScale + ")", () -> { c.hudScale = Math.min(3f, Math.round((c.hudScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Keystrokes", () -> c.keystrokes, v -> { c.keystrokes = v; c.save(); }, left, y, 146);
        toggle("Koordinaten", () -> c.coords, v -> { c.coords = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Toasts", () -> c.notify, v -> { c.notify = v; c.save(); }, left, y, 146);
        toggle("Wasserzeichen", () -> c.watermark, v -> { c.watermark = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Sound", () -> c.sound, v -> { c.sound = v; c.save(); }, left, y, 146);
        toggle("Intro-Trailer", () -> c.introTrailer, v -> { c.introTrailer = v; c.save(); }, left + 154, y, 146);
    }

    // ---------- Survival ----------

    private static final int[] PCT = {5, 10, 20, 30};

    private static int next(int[] arr, int cur) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == cur) return arr[(i + 1) % arr.length];
        return arr[0];
    }

    private void survivalWarnTab(int y) {
        PoldiConfig c = c();
        toggle("Todespunkt merken", () -> c.deathWaypoint, v -> { c.deathWaypoint = v; c.save(); }, left, y, 146);
        toggle("Pickup-Log", () -> c.pickupLog, v -> { c.pickupLog = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Werkzeug-Warnung", () -> c.toolWarn, v -> { c.toolWarn = v; c.save(); }, left, y, 146);
        button(() -> "Werkzeug bei " + c.toolWarnPct + " %", () -> { c.toolWarnPct = next(PCT, c.toolWarnPct); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Rüstungs-Warnung", () -> c.armorWarn, v -> { c.armorWarn = v; c.save(); }, left, y, 146);
        button(() -> "Rüstung bei " + c.armorWarnPct + " %", () -> { c.armorWarnPct = next(PCT, c.armorWarnPct); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Hunger-Warnung", () -> c.hungerWarn, v -> { c.hungerWarn = v; c.save(); }, left, y, 146);
        int[] hs = {4, 6, 8, 10};
        button(() -> "Hunger bei " + c.hungerWarnAt + "/20", () -> { c.hungerWarnAt = next(hs, c.hungerWarnAt); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Inventar-voll-Warnung", () -> c.invFullWarn, v -> { c.invFullWarn = v; c.save(); }, left, y, 146);
        toggle("Saturation-Rand", () -> c.satBorder, v -> { c.satBorder = v; c.save(); }, left + 154, y, 146);
        y += 28;
        labels.add(new Label("Warnungen kommen als Toast unten rechts (+ Ton).", left, y, PoldiTheme.MUTED));
        labels.add(new Label("Todespunkt = Wegpunkt \"Tod\" mit Pfeil im HUD.", left, y + 11, PoldiTheme.MUTED));
    }

    private void survivalWorldTab(int y) {
        PoldiConfig c = c();
        toggle("Licht-Overlay", () -> c.lightOverlay, v -> { c.lightOverlay = v; c.save(); }, left, y, 146);
        toggle("Licht-HUD", () -> c.lightHud, v -> { c.lightHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Tag + Schlafen", () -> c.dayHud, v -> { c.dayHud = v; c.save(); }, left, y, 146);
        toggle("Kompass", () -> c.compassHud, v -> { c.compassHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Biom anzeigen", () -> c.coordsBiome, v -> { c.coordsBiome = v; c.save(); }, left, y, 146);
        toggle("Nether-Koordinaten", () -> c.coordsNether, v -> { c.coordsNether = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Mob-Leben (Ziel)", () -> c.targetHud, v -> { c.targetHud = v; c.save(); }, left, y, 146);
        toggle("Haltbarkeit-HUD", () -> c.armorHud, v -> { c.armorHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Wegpunkte", () -> c.wpOn, v -> { c.wpOn = v; c.save(); }, left, y, 146);
        button(() -> "Wegpunkt hier setzen", () -> {
            String n = "WP" + (c.waypoints.size() + 1);
            PoldiClient.chat(PoldiWaypoints.addHere(n) ? "Wegpunkt \"" + n + "\" gesetzt." : "Nicht möglich.");
        }, left + 154, y, 146, 20, true);
        y += 28;
        labels.add(new Label("Licht-Overlay: rot = Monster spawnen immer,", left, y, PoldiTheme.MUTED));
        labels.add(new Label("gelb = nur nachts (Blocklicht 0, Himmel offen).", left, y + 11, PoldiTheme.MUTED));
    }

    // ---------- HUD ----------

    private void hudTab(int y) {
        PoldiConfig c = c();
        toggle("Keystrokes", () -> c.keystrokes, v -> { c.keystrokes = v; c.save(); }, left, y, 146);
        toggle("Koordinaten", () -> c.coords, v -> { c.coords = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("FPS", () -> c.showFps, v -> { c.showFps = v; c.save(); }, left, y, 146);
        toggle("Ping", () -> c.ping, v -> { c.ping = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Speed", () -> c.speed, v -> { c.speed = v; c.save(); }, left, y, 146);
        toggle("Uhrzeit", () -> c.clock, v -> { c.clock = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Modul-Liste", () -> c.moduleList, v -> { c.moduleList = v; c.save(); }, left, y, 146);
        toggle("Twitch-Chat", () -> c.twitch, v -> { c.twitch = v; c.save(); }, left + 154, y, 146);
        y += 28;
        button(() -> "HUD kleiner", () -> { c.hudScale = Math.max(0.5f, Math.round((c.hudScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "HUD größer", () -> { c.hudScale = Math.min(3f, Math.round((c.hudScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
    }

    // ---------- Design ----------

    private static final String[][] PRESETS = {
        {"Rot", "#DC2626", "#F87171"}, {"Blutrot", "#B91C1C", "#FCA5A5"}, {"Feuer", "#EF4444", "#F97316"},
        {"Gold", "#F59E0B", "#FDE68A"}, {"Rosa", "#EC4899", "#F9A8D4"}, {"Lila", "#A855F7", "#C4B5FD"},
        {"Ozean", "#2563EB", "#22D3EE"}, {"Eis", "#38BDF8", "#E0F2FE"}, {"Wald", "#16A34A", "#BBF7D0"},
        {"Mint", "#34D399", "#7DD3FC"}, {"Grau", "#9CA3AF", "#E5E7EB"}, {"Neon", "#F472B6", "#22D3EE"},
    };

    // Name, Farbe 1, Farbe 2, Hintergrund-Grundfarbe, Deckkraft Hintergrund
    private static final Object[][] THEMES = {
        {"Standard", "#DC2626", "#F87171", "#0C0505", 208},
        {"Amoled", "#DC2626", "#F87171", "#000000", 255},
        {"Nether", "#B91C1C", "#F97316", "#140404", 224},
        {"Glas", "#DC2626", "#F87171", "#0C0505", 110},
        {"Stein", "#9CA3AF", "#E5E7EB", "#101010", 216},
        {"Mitternacht", "#6366F1", "#38BDF8", "#050B1E", 224},
    };

    private void designTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("Farb-Vorlagen", left, y, PoldiTheme.MUTED));
        for (int i = 0; i < PRESETS.length; i++) {
            final String[] p = PRESETS[i];
            button(() -> p[0], () -> { c.color1 = p[1]; c.color2 = p[2]; c.save(); PoldiTheme.reload(c); reopen(); },
                left + (i % 3) * 102, y + 11 + (i / 3) * 22, 96, 20);
        }
        int y1 = y + 104;
        labels.add(new Label("Farbe 1 (#RRGGBB)", left, y1, PoldiTheme.MUTED));
        labels.add(new Label("Farbe 2 (#RRGGBB)", left + 154, y1, PoldiTheme.MUTED));
        field(left, y1 + 10, 146, c.color1, s -> { Integer v = PoldiTheme.parseHex(s); if (v != null) { c.color1 = PoldiTheme.toHex(v); c.save(); PoldiTheme.reload(c); } });
        field(left + 154, y1 + 10, 146, c.color2, s -> { Integer v = PoldiTheme.parseHex(s); if (v != null) { c.color2 = PoldiTheme.toHex(v); c.save(); PoldiTheme.reload(c); } });
        int y2 = y + 146;
        button(() -> "Hintergrund heller", () -> { c.bgOpacity = Math.max(32, c.bgOpacity - 24); c.save(); PoldiTheme.reload(c); }, left, y2, 146, 20);
        button(() -> "Hintergrund dunkler", () -> { c.bgOpacity = Math.min(255, c.bgOpacity + 24); c.save(); PoldiTheme.reload(c); }, left + 154, y2, 146, 20);
        int y3 = y + 176;
        labels.add(new Label("Themes (Farben + Hintergrund)", left, y3, PoldiTheme.MUTED));
        for (int i = 0; i < THEMES.length; i++) {
            final Object[] th = THEMES[i];
            button(() -> (String) th[0], () -> {
                c.color1 = (String) th[1]; c.color2 = (String) th[2]; c.themeBase = (String) th[3]; c.bgOpacity = (Integer) th[4];
                c.save(); PoldiTheme.reload(c); reopen();
            }, left + (i % 3) * 102, y3 + 11 + (i / 3) * 22, 96, 20, true);
        }
    }

    // ---------- Anzeige ----------

    private void visualsTab(int y) {
        PoldiConfig c = c();
        toggle("Wasserzeichen", () -> c.watermark, v -> { c.watermark = v; c.save(); }, left, y, 146);
        toggle("Intro-Trailer", () -> c.introTrailer, v -> { c.introTrailer = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Inventar-Design", () -> c.niceInventory, v -> { c.niceInventory = v; c.save(); }, left, y, 146);
        toggle("Button-Design", () -> c.niceButtons, v -> { c.niceButtons = v; c.save(); PoldiGlass.syncButtons(); }, left + 154, y, 146);
        y += 24;
        toggle("Menü-Hintergrund", () -> c.menuBackground, v -> { c.menuBackground = v; c.save(); }, left, y, 146);
        y += 24;
        glassOn = PoldiGlass.isOn();
        button(() -> "Glas (/glass): " + (glassOn ? "AN" : "AUS"), () -> {
            if (PoldiGlass.toggle()) glassOn = !glassOn; else PoldiClient.chat("Glas-Pack nicht gefunden.");
        }, left, y, 146, 20);
        button(() -> "Trailer ansehen", () -> open(new PoldiTrailerScreen(this)), left + 154, y, 146, 20, true);
        labels.add(new Label("Glas lädt Texturen neu (kurzer Ruckler).", left, y + 24, PoldiTheme.MUTED));
        y += 40;
        toggle("Haltbarkeit-HUD", () -> c.armorHud, v -> { c.armorHud = v; c.save(); }, left, y, 146);
        toggle("Effekte-HUD", () -> c.potionHud, v -> { c.potionHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Session-HUD", () -> c.sessionHud, v -> { c.sessionHud = v; c.save(); }, left, y, 146);
        toggle("Auto-Sprint", () -> c.autoSprint, v -> { c.autoSprint = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "Mauszeiger: " + PoldiCursor.STYLES[Math.floorMod(c.cursorStyle, PoldiCursor.STYLES.length)],
            () -> { c.cursorStyle = (c.cursorStyle + 1) % PoldiCursor.STYLES.length; c.save(); }, left, y, 146, 20, true);
        button(() -> "Zeiger-Größe: " + (int) (PoldiCursor.SIZES[Math.floorMod(c.cursorSize, PoldiCursor.SIZES.length)] * 100) + " %",
            () -> { c.cursorSize = (c.cursorSize + 1) % PoldiCursor.SIZES.length; c.save(); }, left + 154, y, 146, 20);
    }

    // ---------- FPS ----------

    private void perfTab(int y) {
        PoldiConfig c = c();
        toggle("Partikel minimal", () -> c.perfParticles, v -> { c.perfParticles = v; PoldiPerf.apply(); }, left, y, PW);
        y += 24;
        toggle("Entity-Schatten aus", () -> c.perfShadows, v -> { c.perfShadows = v; PoldiPerf.apply(); }, left, y, PW);
        y += 24;
        toggle("Weniger Distanz (Render 8, Simulation 6)", () -> c.perfDistance, v -> { c.perfDistance = v; PoldiPerf.apply(); }, left, y, PW);
        y += 24;
        toggle("Wolken aus", () -> c.perfClouds, v -> { c.perfClouds = v; PoldiPerf.apply(); }, left, y, 146);
        toggle("Biom-Blend 0", () -> c.perfBlend, v -> { c.perfBlend = v; PoldiPerf.apply(); }, left + 154, y, 146);
        y += 24;
        toggle("Weiches Licht aus", () -> c.perfAo, v -> { c.perfAo = v; PoldiPerf.apply(); }, left, y, 146);
        toggle("Kein FPS-Limit", () -> c.perfFps, v -> { c.perfFps = v; PoldiPerf.apply(); }, left + 154, y, 146);
        y += 30;
        labels.add(new Label("Ändert nur normale Spieloptionen. Beim Ausschalten", left, y, PoldiTheme.MUTED));
        labels.add(new Label("werden deine alten Werte wiederhergestellt.", left, y + 11, PoldiTheme.MUTED));
        labels.add(new Label("Tipp: Für noch mehr FPS zusätzlich Sodium nutzen.", left, y + 28, PoldiTheme.MUTED));
    }

    // ---------- Tools ----------

    private void toolsTab(int y) {
        button(() -> "Rechner öffnen", () -> open(new PoldiCalculatorScreen(this)), left, y, 146, 20, true);
        button(() -> "Timer: 1 Minute", () -> { PoldiEvents.addTimer(60, "1 Minute"); PoldiClient.chat("Timer gestartet: 60 s"); }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "Timer: 5 Minuten", () -> { PoldiEvents.addTimer(300, "5 Minuten"); PoldiClient.chat("Timer gestartet: 300 s"); }, left, y, 146, 20);
        button(() -> "Timer löschen", () -> PoldiClient.chat(PoldiEvents.cancelTimers() + " Timer gelöscht."), left + 154, y, 146, 20);
        y += 24;
        button(() -> PoldiEvents.swActive() && PoldiEvents.swElapsed() > 0 ? "Stoppuhr: " + PoldiEvents.swFmt(PoldiEvents.swElapsed()) : "Stoppuhr starten",
            () -> PoldiClient.chat(PoldiEvents.stopwatch(PoldiEvents.swRunning() ? "stop" : "start")), left, y, 146, 20, true);
        button(() -> "Runde", () -> PoldiClient.chat(PoldiEvents.stopwatch("lap")), left + 154, y, 70, 20);
        button(() -> "Reset", () -> PoldiClient.chat(PoldiEvents.stopwatch("reset")), left + 228, y, 72, 20);
        y += 24;
        button(() -> "Lag-Analyse", () -> { for (String ln : PoldiProf.analyze()) PoldiClient.chat(ln); }, left, y, 146, 20, true);
        button(() -> "Sell-Makro …", () -> open(new PoldiSellScreen(this)), left + 154, y, 146, 20, true);
    }

    private static PoldiConfig c() { return PoldiConfig.get(); }

    // ---------- Sell ----------

    private static String ms(int v) { return v % 1000 == 0 ? (v / 1000) + " s" : String.format(java.util.Locale.ROOT, "%.1f s", v / 1000.0); }

    private static int stepMs(int v, int dir) {
        int st = (dir > 0 ? v >= 3000 : v > 3000) ? 1000 : 100;
        return Math.max(100, Math.min(3_600_000, v + dir * st));
    }

    private void sellTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("Sell-Befehl (Standard /sell)", left, y, PoldiTheme.MUTED));
        field(left, y + 10, PW, c.sellCommand == null ? "" : c.sellCommand, s -> { c.sellCommand = s; c.save(); });
        y += 34;
        toggle("Auto", () -> c.sellAuto, v -> { c.sellAuto = v; c.save(); }, left, y, 146);
        button(() -> "-", () -> { c.sellIntervalMs = stepMs(c.sellIntervalMs, -1); c.save(); }, left + 154, y, 30, 20);
        button(() -> "alle " + ms(c.sellIntervalMs), () -> {}, left + 188, y, 54, 20);
        button(() -> "+", () -> { c.sellIntervalMs = stepMs(c.sellIntervalMs, 1); c.save(); }, left + 246, y, 30, 20);
        y += 24;
        button(() -> PoldiMacro.isRunning() ? "Sell-Makro stoppen" : "Sell-Makro starten", PoldiMacro::toggle, left, y, 146, 20, true);
        button(() -> "Sell-Items wählen …", () -> open(new PoldiSellScreen(this)), left + 154, y, 146, 20, true);
        y += 24;
        toggle("Immer ganzes Inv.", () -> c.sellAlwaysAll, v -> { c.sellAlwaysAll = v; c.save(); }, left, y, 146);
        toggle("Shift = ganzes Inv.", () -> c.sellShiftAll, v -> { c.sellShiftAll = v; c.save(); }, left + 154, y, 146);
        labels.add(new Label("Auto: startet, sobald gewählte Items da sind.", left, y + 26, PoldiTheme.MUTED));
        labels.add(new Label("Ganzes Inventar per Shift-Klick auf einmal", left, y + 38, PoldiTheme.MUTED));
        labels.add(new Label("ins Sell-Menü (auch nicht gewählte Items!)", left, y + 49, PoldiTheme.MUTED));
    }

    // ---------- Extras ----------

    private void extrasTab(int y) {
        PoldiConfig c = c();
        toggle("Fullbright", () -> c.fullbright, v -> { c.fullbright = v; c.save(); }, left, y, 146);
        toggle("Item-Highlight", () -> c.itemGlow, v -> { c.itemGlow = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Block-Highlight", () -> c.blockHighlight, v -> { c.blockHighlight = v; c.save(); }, left, y, 146);
        toggle("Anti-Leak", () -> c.antiLeak, v -> { c.antiLeak = v; c.save(); if (!PoldiGlass.setCloak(v)) PoldiClient.chat("Anti-Leak-Pack nicht gefunden."); }, left + 154, y, 146);
        y += 24;
        toggle("Eigener Nametag (F5)", () -> c.ownNametag, v -> { c.ownNametag = v; c.save(); }, left, y, 146);
        toggle("NoFog", () -> c.noFog, v -> { c.noFog = v; c.save(); }, left + 154, y, 146);
        y += 28;
        labels.add(new Label("Anti-Leak: gesperrte Wörter (Komma getrennt)", left, y, PoldiTheme.MUTED));
        EditBox f = field(left, y + 10, PW, c.leakWords, s -> { c.leakWords = s; c.save(); });
        f.setMaxLength(300);
        labels.add(new Label("Sperrt auch Koordinaten, IPs, PC-Namen. Erze = Netherite.", left, y + 32, PoldiTheme.MUTED));
    }

    // ---------- Komfort ----------

    private void comfortTab(int y) {
        PoldiConfig c = c();
        toggle("Autoclicker", () -> c.autoClick, v -> { c.autoClick = v; c.save(); }, left, y, 146);
        button(() -> "Taste: " + (c.autoRight ? "Rechts" : "Links"), () -> { c.autoRight = !c.autoRight; c.save(); }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "-", () -> { c.autoCps = Math.max(1, c.autoCps - 1); c.save(); }, left, y, 30, 20);
        button(() -> "CPS: " + c.autoCps, () -> {}, left + 34, y, 232, 20);
        button(() -> "+", () -> { c.autoCps = Math.min(40, c.autoCps + 1); c.save(); }, left + 270, y, 30, 20);
        y += 24;
        toggle("Auto-Reconnect", () -> c.autoReconnect, v -> { c.autoReconnect = v; c.save(); }, left, y, 146);
        int[] st = {3, 5, 10, 30};
        button(() -> "Reconnect-Pause: " + c.reconnectSec + " s", () -> { c.reconnectSec = next(st, c.reconnectSec); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "Totem-Sound: " + PoldiMisc.TOTEM[c.totemIdx % PoldiMisc.TOTEM.length][0],
            () -> { c.totemIdx = (c.totemIdx + 1) % PoldiMisc.TOTEM.length; c.save(); PoldiMisc.playTotem(c.totemIdx); }, left, y, 200, 20, true);
        button(() -> "Test", () -> PoldiMisc.playTotem(c.totemIdx), left + 204, y, 96, 20);
    }

    // ---------- Streaming: Twitch + Giveaway ----------

    private void eventsTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("Twitch-Kanal (Name)", left, y, PoldiTheme.MUTED));
        field(left, y + 10, PW, c.twitchChannel, s -> { c.twitchChannel = s.trim(); c.save(); });
        y += 34;
        toggle("Twitch-Chat", () -> c.twitch, v -> { c.twitch = v; c.save(); }, left, y, 146);
        button(() -> "Zeilen: " + c.twitchLines, () -> { c.twitchLines = c.twitchLines >= 15 ? 3 : c.twitchLines + 1; c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Ansagen im Chat", () -> c.giveawayAnnounce, v -> { c.giveawayAnnounce = v; c.save(); }, left, y, 146);
        button(() -> "Gewinner ziehen", PoldiEvents::drawWinner, left + 154, y, 146, 20, true);
        y += 28;
        labels.add(new Label("Giveaway-Stichwort", left, y, PoldiTheme.MUTED));
        field(left, y + 10, 146, c.giveawayKeyword, s -> { c.giveawayKeyword = s.trim(); c.save(); });
        y += 34;
        labels.add(new Label("/giveaway start <Sek> <Preis> · /giveaway end", left, y, PoldiTheme.MUTED));
        labels.add(new Label("Teilnahme: Spieler schreibt das Stichwort in den Chat.", left, y + 11, PoldiTheme.MUTED));
    }

    // ---------- YouTube ----------

    private void youtubeTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("YouTube API-Key", left, y, PoldiTheme.MUTED));
        field(left, y + 10, PW, c.youtubeKey, s -> { c.youtubeKey = s.trim(); c.save(); });
        y += 34;
        labels.add(new Label("Live-Video (Link oder ID)", left, y, PoldiTheme.MUTED));
        field(left, y + 10, PW, c.youtubeVideo, s -> { c.youtubeVideo = s.trim(); c.save(); });
        y += 34;
        toggle("YouTube-Chat", () -> c.youtube, v -> { c.youtube = v; c.save(); }, left, y, 146);
        button(() -> "Status: " + PoldiYoutube.state, () -> {}, left + 154, y, 146, 20);
        y += 28;
        labels.add(new Label("Gratis-Key: Google Cloud Console → \"YouTube", left, y, PoldiTheme.MUTED));
        labels.add(new Label("Data API v3\" aktivieren → API-Key erstellen.", left, y + 11, PoldiTheme.MUTED));
        labels.add(new Label("Chat erscheint im Twitch-Chat-HUD (rot).", left, y + 22, PoldiTheme.MUTED));
    }

    // ---------- Mehr HUDs ----------

    private void moreHudTab(int y) {
        PoldiConfig c = c();
        toggle("Spieler in der Nähe", () -> c.nearbyHud, v -> { c.nearbyHud = v; c.save(); }, left, y, 146);
        toggle("Ziel-HUD", () -> c.targetHud, v -> { c.targetHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Combo-Zähler", () -> c.comboHud, v -> { c.comboHud = v; c.save(); }, left, y, 146);
        toggle("System (FPS/RAM)", () -> c.memHud, v -> { c.memHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Status (Leben/Hunger)", () -> c.vitalsHud, v -> { c.vitalsHud = v; c.save(); }, left, y, 146);
        toggle("Notizen-HUD", () -> c.notesHud, v -> { c.notesHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Kompass", () -> c.compassHud, v -> { c.compassHud = v; c.save(); }, left, y, 146);
        toggle("Sell Status", () -> c.sellHud, v -> { c.sellHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Tag + Schlafen", () -> c.dayHud, v -> { c.dayHud = v; c.save(); }, left, y, 146);
        toggle("Licht-HUD", () -> c.lightHud, v -> { c.lightHud = v; c.save(); }, left + 154, y, 146);
    }

    // ---------- Scoreboard ----------

    private void scoreboardTab(int y) {
        PoldiConfig c = c();
        toggle("Eigenes Scoreboard", () -> c.sbCustom, v -> { c.sbCustom = v; c.save(); }, left, y, 146);
        toggle("Zahlen", () -> c.sbNumbers, v -> { c.sbNumbers = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "Transparenter", () -> { c.sbAlpha = Math.max(0, c.sbAlpha - 15); c.save(); }, left, y, 146, 20);
        button(() -> "Deckender (" + Math.round(c.sbAlpha * 100f / 255f) + "%)", () -> { c.sbAlpha = Math.min(255, c.sbAlpha + 15); c.save(); }, left + 154, y, 146, 20);
        y += 26;
        labels.add(new Label("Hintergrund (#RRGGBB)", left, y, PoldiTheme.MUTED));
        labels.add(new Label("Rand (#RRGGBB)", left + 154, y, PoldiTheme.MUTED));
        field(left, y + 10, 146, c.sbBg, s -> { if (PoldiTheme.parseHex(s) != null) { c.sbBg = s; c.save(); } });
        field(left + 154, y + 10, 146, c.sbBorder, s -> { if (PoldiTheme.parseHex(s) != null) { c.sbBorder = s; c.save(); } });
        y += 34;
        labels.add(new Label("Textfarbe (#RRGGBB)", left, y, PoldiTheme.MUTED));
        field(left, y + 10, 146, c.sbText, s -> { if (PoldiTheme.parseHex(s) != null) { c.sbText = s; c.save(); } });
        toggle("Rand", () -> c.sbBorderOn, v -> { c.sbBorderOn = v; c.save(); }, left + 154, y + 9, 146);
        y += 34;
        button(() -> "Kleiner", () -> { c.sbScale = Math.max(0.5f, Math.round((c.sbScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "Größer (" + c.sbScale + ")", () -> { c.sbScale = Math.min(2.5f, Math.round((c.sbScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Design-Farben", () -> c.sbTheme, v -> { c.sbTheme = v; c.save(); }, left, y, 146);
    }

    // ---------- Befehle ----------

    private static final String[] CMD_DEFAULT = {
        "/poldi menu", "/poldi lag", "/poldi perf", "/poldi perf reset", "/poldi lite", "/poldi hud", "/poldi keys", "/poldi sell",
        "/poldi trailer", "/poldi update", "/poldi leak Test", "/poldi scale 1",
        "/calc", "/stopwatch start", "/stopwatch stop", "/stopwatch lap", "/stopwatch reset", "/timer 60 Name", "/timer cancel", "/glass", "/fullbright",
        "/note add Text", "/note list", "/note remove 1", "/note clear",
        "/waypoint add Name", "/waypoint add Name 0 64 0", "/waypoint list", "/waypoint remove Name", "/waypoint clear",
        "/profile save Name", "/profile load Name", "/profile list", "/profile delete Name",
        "/twitch Kanal", "/twitch off", "/youtube Link", "/youtube off",
        "/giveaway start 60 Preis", "/giveaway end", "/giveaway cancel"
    };
    private static final String[] cmdText = CMD_DEFAULT.clone();
    private static int cmdPage = 0;
    private static final int PER_PAGE = 6;

    private static void runCommand(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        String c = text.trim();
        if (c.startsWith("/")) c = c.substring(1);
        if (!c.isEmpty()) mc.player.connection.sendCommand(c);
    }

    private static String cmdCategory(String c) {
        String s = c.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("/poldi")) return "Menüs";
        if (s.startsWith("/note")) return "Notizen";
        if (s.startsWith("/waypoint")) return "Wegpunkte";
        if (s.startsWith("/profile")) return "Profile";
        if (s.startsWith("/twitch") || s.startsWith("/youtube")) return "Chat";
        if (s.startsWith("/giveaway")) return "Events";
        return "Tools";
    }

    private void commandsTab(int y) {
        int pages = (cmdText.length + PER_PAGE - 1) / PER_PAGE;
        cmdPage = Math.max(0, Math.min(pages - 1, cmdPage));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = cmdPage * PER_PAGE + i;
            if (idx >= cmdText.length) break;
            EditBox f = field(left, y + i * 24 + 1, 214, cmdText[idx], s -> cmdText[idx] = s);
            f.setMaxLength(120);
            button(() -> "Ausführen", () -> runCommand(cmdText[idx]), left + 220, y + i * 24, 80, 20, true);
        }
        int ny = y + PER_PAGE * 24 + 2;
        button(() -> "<", () -> { cmdPage = (cmdPage + pages - 1) % pages; reopen(); }, left, ny, 40, 20);
        button(() -> cmdCategory(cmdText[Math.min(cmdText.length - 1, cmdPage * PER_PAGE)]) + " · " + (cmdPage + 1) + "/" + pages, () -> {}, left + 44, ny, 130, 20);
        button(() -> ">", () -> { cmdPage = (cmdPage + 1) % pages; reopen(); }, left + 178, ny, 40, 20);
        button(() -> "Reset", () -> { System.arraycopy(CMD_DEFAULT, 0, cmdText, 0, cmdText.length); reopen(); }, left + 222, ny, 78, 20);
    }

    // ---------- Settings ----------

    private static int presetIdx = 0;

    private void settingsTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("Farbe 1 (#RRGGBB)", left, y, PoldiTheme.MUTED));
        labels.add(new Label("Farbe 2 (#RRGGBB)", left + 154, y, PoldiTheme.MUTED));
        field(left, y + 10, 146, c.color1, s -> { Integer v = PoldiTheme.parseHex(s); if (v != null) { c.color1 = PoldiTheme.toHex(v); c.save(); PoldiTheme.reload(c); } });
        field(left + 154, y + 10, 146, c.color2, s -> { Integer v = PoldiTheme.parseHex(s); if (v != null) { c.color2 = PoldiTheme.toHex(v); c.save(); PoldiTheme.reload(c); } });
        y += 34;
        button(() -> "Vorlage: " + PRESETS[presetIdx % PRESETS.length][0], () -> {
            presetIdx = (presetIdx + 1) % PRESETS.length;
            String[] p = PRESETS[presetIdx];
            c.color1 = p[1]; c.color2 = p[2]; c.save(); PoldiTheme.reload(c); reopen();
        }, left, y, 146, 20, true);
        button(() -> "Heller", () -> { c.bgOpacity = Math.max(32, c.bgOpacity - 24); c.save(); PoldiTheme.reload(c); }, left + 154, y, 72, 20);
        button(() -> "Dunkler", () -> { c.bgOpacity = Math.min(255, c.bgOpacity + 24); c.save(); PoldiTheme.reload(c); }, left + 230, y, 70, 20);
        y += 28;
        labels.add(new Label("Beitreten-Button (Hauptmenü): Name", left, y, PoldiTheme.MUTED));
        labels.add(new Label("Server-IP", left + 154, y, PoldiTheme.MUTED));
        field(left, y + 10, 146, c.serverName, s -> { c.serverName = s.trim(); c.save(); });
        field(left + 154, y + 10, 146, c.serverIp, s -> { c.serverIp = s.trim(); c.save(); });
        y += 34;
        toggle("Beitreten-Button", () -> c.joinButton, v -> { c.joinButton = v; c.save(); }, left, y, 146);
        toggle("Toasts", () -> c.notify, v -> { c.notify = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Auto-Update", () -> c.autoUpdate, v -> { c.autoUpdate = v; c.save(); }, left, y, 146);
        button(() -> "Nach Updates suchen", () -> PoldiUpdater.checkAsync(true), left + 154, y, 146, 20, true);
        y += 22;
        labels.add(new Label("Version " + PoldiUpdater.currentVersion() + " · Update: " + PoldiUpdater.state, left, y + 2, PoldiTheme.MUTED));
        y += 14;
        button(() -> "HUD verschieben", () -> open(new PoldiHudEditor(this)), left, y, PW, 20, true);
    }

    // ---------- PvP ----------

    @SuppressWarnings("unchecked")
    private void pvpTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("Nur Anzeigen & Komfort – keine Kampfhilfen.", left, y, PoldiTheme.MUTED));
        y += 14;
        String[] names = {"CPS-Anzeige", "Reach-Anzeige", "Item-Zähler", "Treffer-Marker", "Treffer-Sound", "Low-HP-Warnung", "Cooldown-Balken",
            "Combo-Zähler", "Ziel-HUD", "Spieler in der Nähe", "Haltbarkeit-HUD", "Effekte", "Keystrokes", "Status (Leben/Hunger)",
            "Saturation-Rand", "Ping", "Auto-Sprint"};
        Supplier<Boolean>[] get = new Supplier[]{
            () -> c.cpsHud, () -> c.reachHud, () -> c.pvpItems, () -> c.hitMarker, () -> c.hitSound, () -> c.lowHpWarn, () -> c.cooldownBar,
            () -> c.comboHud, () -> c.targetHud, () -> c.nearbyHud, () -> c.armorHud, () -> c.potionHud, () -> c.keystrokes, () -> c.vitalsHud,
            () -> c.satBorder, () -> c.ping, () -> c.autoSprint};
        Consumer<Boolean>[] set = new Consumer[]{
            (Consumer<Boolean>) v -> c.cpsHud = v, (Consumer<Boolean>) v -> c.reachHud = v,
            (Consumer<Boolean>) v -> c.pvpItems = v, (Consumer<Boolean>) v -> c.hitMarker = v,
            (Consumer<Boolean>) v -> c.hitSound = v, (Consumer<Boolean>) v -> c.lowHpWarn = v,
            (Consumer<Boolean>) v -> c.cooldownBar = v, (Consumer<Boolean>) v -> c.comboHud = v,
            (Consumer<Boolean>) v -> c.targetHud = v, (Consumer<Boolean>) v -> c.nearbyHud = v,
            (Consumer<Boolean>) v -> c.armorHud = v, (Consumer<Boolean>) v -> c.potionHud = v,
            (Consumer<Boolean>) v -> c.keystrokes = v, (Consumer<Boolean>) v -> c.vitalsHud = v,
            (Consumer<Boolean>) v -> c.satBorder = v, (Consumer<Boolean>) v -> c.ping = v,
            (Consumer<Boolean>) v -> c.autoSprint = v};
        for (int k = 0; k < names.length; k++) {
            final int idx = k;
            toggle(names[k], get[idx], v -> { set[idx].accept(v); c.save(); }, left + (k % 2) * 154, y + (k / 2) * 24, 146);
        }
        int by2 = y + ((names.length + 1) / 2) * 24;
        button(() -> "Treffer-Sound: " + PoldiPvp.SOUNDS[Math.floorMod(c.hitSoundIdx, PoldiPvp.SOUNDS.length)][0], () -> {
            c.hitSoundIdx = (c.hitSoundIdx + 1) % PoldiPvp.SOUNDS.length; c.save(); PoldiPvp.playHit();
        }, left, by2, 200, 20, true);
        button(() -> "Test", PoldiPvp::playHit, left + 204, by2, 96, 20);
    }

    // ---------- Profile ----------

    private static String profName = "";

    private void profileTab(int y) {
        labels.add(new Label("Profilname (Buchstaben, Zahlen, _ -)", left, y, PoldiTheme.MUTED));
        EditBox f = field(left, y + 10, 214, profName, s -> profName = s);
        f.setMaxLength(30);
        button(() -> "Speichern", () -> {
            if (PoldiProfiles.save(profName)) { PoldiClient.chat("Profil gespeichert."); reopen(); }
            else PoldiClient.chat("Bitte einen Namen eingeben.");
        }, left + 218, y + 9, 82, 20, true);
        y += 38;
        var list = PoldiProfiles.list();
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            final String n = list.get(i);
            labels.add(new Label(font.plainSubstrByWidth(n, 190), left, y + 6, PoldiTheme.TEXT));
            button(() -> "Laden", () -> { if (PoldiProfiles.load(n)) { PoldiClient.chat("Profil geladen."); reopen(); } }, left + 200, y, 64, 20, true);
            button(() -> "X", () -> { PoldiProfiles.delete(n); reopen(); }, left + 270, y, 30, 20);
            y += 22;
        }
        if (list.size() > 5) labels.add(new Label("+" + (list.size() - 5) + " weitere (/profile list)", left, y + 4, PoldiTheme.MUTED));
        if (list.isEmpty()) labels.add(new Label("Noch keine Profile.", left, y + 4, PoldiTheme.MUTED));
        labels.add(new Label("Speichert ALLES: Einstellungen, HUD-Positionen, Farben,", left, y + 22, PoldiTheme.MUTED));
        labels.add(new Label("Notizen, Wegpunkte und Tasten.", left, y + 33, PoldiTheme.MUTED));
    }

    // ---------- Wegpunkte ----------

    private static String wpName = "";

    private void waypointTab(int y) {
        PoldiConfig c = c();
        toggle("Wegpunkte", () -> c.wpOn, v -> { c.wpOn = v; c.save(); }, left, y, 146);
        button(() -> "Hier setzen", () -> {
            if (wpName.isBlank()) { PoldiClient.chat("Bitte einen Namen eingeben."); return; }
            PoldiWaypoints.addHere(wpName.trim().replace(' ', '_')); reopen();
        }, left + 154, y, 146, 20, true);
        y += 24;
        labels.add(new Label("Name", left, y, PoldiTheme.MUTED));
        EditBox f = field(left, y + 10, PW, wpName, s -> wpName = s);
        f.setMaxLength(24);
        y += 34;
        int shown = Math.min(5, c.waypoints.size());
        for (int i = 0; i < shown; i++) {
            final PoldiConfig.Wp w = c.waypoints.get(i);
            String pos = c.streamer && c.streamerHideXyz ? "???" : w.x + " " + w.y + " " + w.z;
            labels.add(new Label(font.plainSubstrByWidth(w.name + "  " + pos, 224), left, y + 6, w.name.equals("Tod") ? PoldiTheme.BAD : PoldiTheme.TEXT));
            button(() -> "Löschen", () -> { c.waypoints.remove(w); c.save(); reopen(); }, left + 244, y, 56, 20);
            y += 22;
        }
        if (c.waypoints.size() > shown) labels.add(new Label("+" + (c.waypoints.size() - shown) + " weitere (/waypoint list)", left, y + 4, PoldiTheme.MUTED));
        if (c.waypoints.isEmpty()) labels.add(new Label("/waypoint add <Name> [x y z] · Marker + Pfeil im HUD", left, y + 4, PoldiTheme.MUTED));
    }

    // ---------- Streamer ----------

    private void streamerTab(int y) {
        PoldiConfig c = c();
        toggle("Streamer-Modus", () -> c.streamer, v -> { c.streamer = v; c.save(); }, left, y, PW);
        y += 28;
        labels.add(new Label("Dein Name im Chat/HUD wird ersetzt durch:", left, y, PoldiTheme.MUTED));
        field(left, y + 10, PW, c.streamerName, s -> { c.streamerName = s; c.save(); });
        y += 36;
        toggle("Koordinaten verstecken", () -> c.streamerHideXyz, v -> { c.streamerHideXyz = v; c.save(); }, left, y, PW);
        y += 30;
        labels.add(new Label("Ersetzt deinen Namen in Chat-Nachrichten und im HUD.", left, y, PoldiTheme.MUTED));
    }

    // ---------- Eigenes HUD ----------

    private void customTab(int y) {
        PoldiConfig c = c();
        while (c.customLines.size() < 5) c.customLines.add("");
        toggle("Eigenes HUD", () -> c.customHud, v -> { c.customHud = v; c.save(); }, left, y, 146);
        button(() -> "Platzhalter (Chat)", () -> PoldiClient.chatLong("Platzhalter: " + String.join(" ", PoldiPanels.TOKENS)), left + 154, y, 146, 20, true);
        y += 24;
        labels.add(new Label("Titel", left, y, PoldiTheme.MUTED));
        field(left, y + 10, PW, c.customTitle, s -> { c.customTitle = s; c.save(); });
        y += 34;
        labels.add(new Label("Zeilen (leer = ausgeblendet)", left, y, PoldiTheme.MUTED));
        y += 10;
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            EditBox f = field(left, y + i * 21, PW, c.customLines.get(i), s -> { c.customLines.set(idx, s); c.save(); });
            f.setMaxLength(80);
        }
    }

    // ---------- Notizen ----------

    private static String draft = "";

    private void notesTab(int y) {
        PoldiConfig c = c();
        labels.add(new Label("Neue Notiz", left, y, PoldiTheme.MUTED));
        EditBox f = field(left, y + 10, 214, draft, s -> draft = s);
        f.setMaxLength(120);
        button(() -> "Hinzufügen", () -> { if (!draft.isBlank()) { c.notes.add(draft.trim()); draft = ""; c.save(); reopen(); } }, left + 218, y + 9, 82, 20, true);
        y += 34;
        toggle("Notizen-HUD", () -> c.notesHud, v -> { c.notesHud = v; c.save(); }, left, y, PW);
        y += 26;
        int shown = Math.min(4, c.notes.size());
        for (int i = 0; i < shown; i++) {
            final int idx = i;
            labels.add(new Label((i + 1) + ". " + font.plainSubstrByWidth(c.notes.get(i), 224), left, y + 6, PoldiTheme.TEXT));
            button(() -> "Löschen", () -> { if (idx < c.notes.size()) { c.notes.remove(idx); c.save(); reopen(); } }, left + 244, y, 56, 20);
            y += 22;
        }
        if (c.notes.size() > shown) labels.add(new Label("+" + (c.notes.size() - shown) + " weitere (/note list)", left, y + 4, PoldiTheme.MUTED));
        if (c.notes.isEmpty()) labels.add(new Label("/note add <Text> · /note remove <Nr>", left, y + 4, PoldiTheme.MUTED));
    }

    // ---------- Helper ----------

    private void toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set, int x, int y, int w) {
        button(() -> name + ": " + (get.get() ? "AN" : "AUS"), () -> set.accept(!get.get()), x, y, w, 20);
    }

    private EditBox field(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox f = new EditBox(font, x, y, w, 18, Component.empty());
        f.setMaxLength(60);
        f.setValue(value == null ? "" : value);
        f.setResponder(onChange);
        addRenderableWidget(f);
        return f;
    }

    // ---------- Rendering ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean inWorld = Minecraft.getInstance().level != null;
        darkBackground(g, inWorld ? 0x60 : 0xF0);
        PoldiTheme.panel(g, left - 12, top - 28, PW + 24, panelBottom + 28, inWorld ? 0xC8 : 0xFF);
        PoldiLogo.draw(g, left, top - 22, 12);
        PoldiTheme.gradientText(g, font, PoldiClient.NAME, left + 16, top - 20);
    }

    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Label l : labels) {
            int ly = l.y() - scroll;
            if (ly >= bandTop - 2 && ly + 9 <= bandTop + bandH + 2) PoldiTheme.text(g, font, PoldiTheme.ui(l.text()), l.x(), ly, l.color());
        }
        if (maxScroll() > 0) {
            int th = Math.max(16, bandH * bandH / contentH);
            int ty = bandTop + (int) ((bandH - th) * (scroll / (float) maxScroll()));
            PoldiTheme.bevel(g, left + PW + 3, ty, 5, th, PoldiTheme.PRIMARY, false, true);
        }
        boolean searching = !query.trim().isEmpty();
        if (!searching) {
            int tw = PW / 4, cc = catOf(tab);
            PoldiTheme.outline(g, left + (cc % 4) * tw - 1, top + (cc / 4) * 17 - 1, tw, 18, 0xFFFFFFFF);
        }
        String here = searching ? "Suche" : CATS[catOf(tab)] + " › " + TABS[tab];
        int hx = left + 16 + font.width(PoldiClient.NAME) + 6;
        PoldiTheme.text(g, font, "›", hx, top - 20, PoldiTheme.MUTED);
        PoldiTheme.text(g, font, PoldiTheme.ui(here), hx + 10, top - 20, PoldiTheme.SECOND);
        Component hint = PoldiTheme.ui("v" + PoldiClient.VERSION + " · Right Shift = Menü");
        PoldiTheme.text(g, font, hint, left + (PW - font.width(hint)) / 2, top + panelBottom - 14, PoldiTheme.MUTED);
    }

    @Override
    public void onClose() { open(parent); }
}
