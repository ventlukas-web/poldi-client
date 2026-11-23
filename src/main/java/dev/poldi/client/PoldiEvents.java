package dev.poldi.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Giveaway (Teilnahme per Chat-Stichwort), Stoppuhr und Timer. */
public final class PoldiEvents {
    private PoldiEvents() {}

    // Giveaway
    public static boolean gwOn;
    private static String gwPrize = "";
    private static long gwEnd;
    private static final LinkedHashSet<String> entrants = new LinkedHashSet<>();
    private static final Random RND = new Random();

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (PoldiConfig.get().giveawayAnnounce && mc.player.connection != null) mc.player.connection.sendChat(s);
        else PoldiClient.chat(s);
    }

    public static void startGiveaway(int sec, String prize) {
        gwOn = true; gwPrize = prize; entrants.clear();
        gwEnd = System.currentTimeMillis() + sec * 1000L;
        say("GIVEAWAY: " + prize + " | Schreib \"" + PoldiConfig.get().giveawayKeyword + "\" in den Chat | " + sec + "s");
        PoldiNotify.push("Giveaway", prize, PoldiTheme.SECOND);
    }

    public static void drawWinner() {
        if (!gwOn) { PoldiClient.chat("Kein Giveaway aktiv."); return; }
        if (entrants.isEmpty()) { gwOn = false; say("Giveaway beendet – keine Teilnehmer."); return; }
        List<String> l = new ArrayList<>(entrants);
        String w = l.get(RND.nextInt(l.size()));
        gwOn = false;
        say("Giveaway-Gewinner: " + w + " (" + gwPrize + ")! " + l.size() + " Teilnehmer.");
        PoldiNotify.push("Giveaway", w + " gewinnt!", PoldiTheme.GOOD);
    }

    public static void cancelGiveaway() { gwOn = false; PoldiClient.chat("Giveaway abgebrochen."); }

    /** Chatzeile pruefen: enthaelt sie einen Spielernamen gefolgt vom Stichwort? */
    public static void onChatLine(String text) {
        if (!gwOn) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        String kw = PoldiConfig.get().giveawayKeyword.toLowerCase().trim();
        if (kw.isEmpty()) return;
        String low = text.toLowerCase();
        for (var e : mc.getConnection().getOnlinePlayers()) {
            String n = e.getProfile().name();
            int i = low.indexOf(n.toLowerCase());
            if (i < 0) continue;
            int k = low.indexOf(kw, i + n.length());
            if (k >= 0) { if (entrants.add(n)) PoldiNotify.push("Giveaway", n + " nimmt teil (" + entrants.size() + ")", PoldiTheme.SECOND); return; }
        }
    }

    // ---------- Stoppuhr (/stopwatch) ----------
    private static long swStart = 0, swAcc = 0;
    private static boolean swRun = false;
    private static final List<Long> LAPS = new ArrayList<>();

    public static long swElapsed() { return swAcc + (swRun ? System.currentTimeMillis() - swStart : 0); }
    public static boolean swRunning() { return swRun; }
    public static boolean swActive() { return swRun || swAcc > 0; }

    public static String swFmt(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100);
    }

    public static String stopwatch(String cmd) {
        switch (cmd) {
            case "start" -> { if (!swRun) { swStart = System.currentTimeMillis(); swRun = true; } return "Stoppuhr läuft."; }
            case "stop" -> { if (swRun) { swAcc = swElapsed(); swRun = false; } return "Stoppuhr gestoppt: " + swFmt(swAcc); }
            case "lap" -> { long t = swElapsed(); LAPS.add(t); return "Runde " + LAPS.size() + ": " + swFmt(t); }
            default -> { swRun = false; swAcc = 0; LAPS.clear(); return "Stoppuhr zurückgesetzt."; }
        }
    }

    // ---------- Timer (/timer) ----------
    private static final List<Object[]> TIMERS = new ArrayList<>();

    public static void addTimer(int sec, String name) { TIMERS.add(new Object[]{System.currentTimeMillis() + sec * 1000L, name}); }

    public static List<String> timerLines() {
        List<String> l = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Object[] t : TIMERS) {
            long s = Math.max(0, ((Long) t[0] - now + 999) / 1000);
            l.add("Timer " + t[1] + "  " + (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60));
        }
        return l;
    }

    public static int cancelTimers() { int n = TIMERS.size(); TIMERS.clear(); return n; }

    public static void tick() {
        if (!TIMERS.isEmpty()) {
            long now = System.currentTimeMillis();
            for (var it = TIMERS.iterator(); it.hasNext(); ) {
                Object[] t = it.next();
                if (now >= (Long) t[0]) {
                    it.remove();
                    PoldiNotify.push("Timer fertig", (String) t[1], PoldiTheme.GOOD);
                    if (PoldiConfig.get().sound) PoldiClient.sound("block.note_block.bell");
                }
            }
        }
        PoldiPanels.armorCheck();
        if (gwOn && System.currentTimeMillis() >= gwEnd) drawWinner();
    }

    /** Zeilen fuer das HUD: {links, rechts}. */
    public static List<String[]> rows() {
        List<String[]> r = new ArrayList<>();
        if (gwOn) {
            r.add(new String[]{"Giveaway: " + gwPrize, Math.max(0, (gwEnd - System.currentTimeMillis()) / 1000) + "s"});
            r.add(new String[]{"Teilnehmer", String.valueOf(entrants.size())});
        }
        return r;
    }
}
