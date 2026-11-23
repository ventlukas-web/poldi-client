package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Sell-Makro: /sell oeffnen -> nur die gewaehlten Items per Shift-Klick ins Menue schieben ->
 * wenn nichts mehr hineinpasst (Menue voll) oder keine gewaehlten Items mehr da sind: Menue schliessen
 * (= verkaufen, wie ESC) -> kurz warten -> erneut /sell, solange gewaehlte Items im Inventar liegen.
 */
public final class PoldiMacro {
    private enum S { IDLE, OPENING, WAIT_OPEN, FILLING, FILL_WAIT, CLOSING, WAIT_REOPEN }

    private static S state = S.IDLE;
    private static int timer, waitTicks, cycles, strikes, lastCount, cycleStartCount, noProgress;
    private static long lastAuto;
    public static long soldItems, soldSince;

    public static void resetSold() { soldItems = 0; soldSince = 0; }

    public static String statusText() {
        PoldiConfig cfg = PoldiConfig.get();
        if (state != S.IDLE) return "läuft";
        if (!cfg.sellAuto) return "bereit";
        long left = Math.max(0, Math.max(100, cfg.sellIntervalMs) - (System.currentTimeMillis() - lastAuto));
        return String.format(java.util.Locale.ROOT, "Auto in %.1fs", left / 1000.0);
    }

    public static int cycleCount() { return cycles; }
    private static boolean burstMode;

    private static boolean wantAll() {
        PoldiConfig c = PoldiConfig.get();
        return c.sellAlwaysAll || (c.sellShiftAll && shiftHeld());
    }

    private static boolean shiftHeld() {
        long w = Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
            || org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
    }

    private static int countAll(Player p) {
        int n = 0;
        for (int i = 0; i < 36; i++) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private PoldiMacro() {}

    public static boolean isRunning() { return state != S.IDLE; }

    /** Taste/Knopf: startet einen Durchlauf bzw. stoppt einen laufenden. */
    public static void toggle() {
        if (isRunning()) stop("Sell-Makro gestoppt.");
        else start(true);
    }

    public static boolean runOnce(boolean feedback) { return start(feedback); }

    private static Set<String> selected() { return new HashSet<>(PoldiConfig.get().sellItems); }

    private static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }

    private static boolean start(boolean feedback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;
        boolean all = wantAll();
        if (!all && PoldiConfig.get().sellItems.isEmpty()) {
            if (feedback) PoldiClient.chat("Keine Sell-Items gewählt (Menü → Tools → Sell).");
            return false;
        }
        if (all ? countAll(mc.player) == 0 : countInventory(mc.player) == 0) {
            if (feedback) PoldiClient.chat("Keine gewählten Items im Inventar.");
            return false;
        }
        cycles = 0; noProgress = 0; burstMode = false;
        state = S.OPENING; timer = 0;
        if (feedback) PoldiClient.chat("Sell-Makro gestartet.");
        return true;
    }

    private static void stop(String msg) {
        state = S.IDLE;
        if (msg != null) PoldiClient.chat(msg);
    }

    /** Anzahl gewaehlter Items im eigenen Inventar (Hotbar + Hauptinventar). */
    private static int countInventory(Player p) {
        Set<String> sel = selected();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && sel.contains(id(s))) n += s.getCount();
        }
        return n;
    }

    private static boolean sellScreenOpen(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ContainerScreen gs)) return false;
        String f = PoldiConfig.get().sellTitleFilter;
        return f == null || f.isBlank() || gs.getTitle().getString().toLowerCase().contains(f.toLowerCase().trim());
    }

    private static int countInHandler(AbstractContainerMenu h) {
        Set<String> sel = selected();
        int n = 0;
        for (Slot s : h.slots) {
            if (!(s.container instanceof Inventory)) continue;
            ItemStack st = s.getItem();
            if (!st.isEmpty() && sel.contains(id(st))) n += st.getCount();
        }
        return n;
    }

    private static Slot nextSelectedSlot(AbstractContainerMenu h) {
        Set<String> sel = selected();
        for (Slot s : h.slots) {
            if (!(s.container instanceof Inventory)) continue;
            ItemStack st = s.getItem();
            if (!st.isEmpty() && sel.contains(id(st))) return s;
        }
        return null;
    }

    public static void tick(Minecraft mc) {
        PoldiConfig cfg = PoldiConfig.get();
        if (mc.player == null || mc.gameMode == null) { state = S.IDLE; return; }

        if (state == S.IDLE) {
            long interval = Math.max(100, cfg.sellIntervalMs);
            long now = System.currentTimeMillis();
            if (cfg.sellAuto && mc.gui.screen() == null && now - lastAuto >= interval) {
                lastAuto = now;
                start(false);
            }
            return;
        }
        if (timer > 0) { timer--; return; }

        switch (state) {
            case OPENING -> {
                String cmd = cfg.sellCommand == null || cfg.sellCommand.isBlank() ? "/sell" : cfg.sellCommand.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                cycleStartCount = burstMode ? countAll(mc.player) : countInventory(mc.player);
                mc.player.connection.sendCommand(cmd);
                state = S.WAIT_OPEN; waitTicks = 0;
            }
            case WAIT_OPEN -> {
                if (sellScreenOpen(mc)) { state = S.FILLING; strikes = 0; timer = 2; }
                else if (++waitTicks > 40) stop("Sell-Menü ging nicht auf – Makro gestoppt.");
            }
            case FILLING -> {
                if (!sellScreenOpen(mc) || !(mc.gui.screen() instanceof ContainerScreen gs)) {
                    stop("Sell-Menü wurde geschlossen – Makro gestoppt.");
                    return;
                }
                AbstractContainerMenu h = gs.getMenu();
                if (wantAll()) {
                    // Shift gehalten: ganzes Inventar auf einmal in das Menue schieben
                    burstMode = true;
                    for (Slot bs : h.slots) {
                        if (!(bs.container instanceof Inventory) || !bs.hasItem()) continue;
                        quickMove(mc, h.containerId, bs.index);
                    }
                    state = S.CLOSING; timer = 2;
                    return;
                }
                Slot s = nextSelectedSlot(h);
                if (s == null) { state = S.CLOSING; return; }
                lastCount = countInHandler(h);
                quickMove(mc, h.containerId, s.index);
                state = S.FILL_WAIT;
                timer = Math.max(0, cfg.sellClickDelay);
            }
            case FILL_WAIT -> {
                if (!(mc.gui.screen() instanceof ContainerScreen gs)) { stop("Sell-Menü wurde geschlossen – Makro gestoppt."); return; }
                int now = countInHandler(gs.getMenu());
                // nichts mehr verschoben worden = Menue voll
                if (now >= lastCount) strikes++; else strikes = 0;
                state = strikes >= 2 ? S.CLOSING : S.FILLING;
            }
            case CLOSING -> {
                mc.player.closeContainer(); // wie ESC -> Server verkauft
                cycles++;
                state = S.WAIT_REOPEN; timer = 8;
            }
            case WAIT_REOPEN -> {
                int left = burstMode ? countAll(mc.player) : countInventory(mc.player);
                if (left < cycleStartCount) {
                    soldItems += cycleStartCount - left;
                    if (soldSince == 0) soldSince = System.currentTimeMillis();
                }
                if (left == 0) { state = S.IDLE; PoldiClient.chat("Sell-Makro fertig."); return; }
                if (left >= cycleStartCount) noProgress++; else noProgress = 0;
                if (noProgress >= 3) { stop("Kein Fortschritt beim Verkaufen – Makro gestoppt."); return; }
                if (cycles >= 200) { stop("Maximale Durchläufe erreicht – Makro gestoppt."); return; }
                state = S.OPENING; timer = 2;
            }
            default -> {}
        }
    }

    private static java.lang.reflect.Method clickMethod;

    /** Shift-Klick auf einen Slot. Die Methode wird per Typ gesucht (Name/Reihenfolge aendern sich zwischen Versionen). */
    private static void quickMove(Minecraft mc, int containerId, int slot) {
        try {
            if (clickMethod == null) {
                for (var m : mc.gameMode.getClass().getMethods()) {
                    var pt = m.getParameterTypes();
                    boolean hasInput = false;
                    for (var t : pt) if (t == ContainerInput.class) hasInput = true;
                    if (hasInput && pt.length == 5) { clickMethod = m; break; }
                }
            }
            if (clickMethod == null) return;
            var pt = clickMethod.getParameterTypes();
            Object[] args = new Object[pt.length];
            int[] ints = {containerId, slot, 0};
            int k = 0;
            for (int i = 0; i < pt.length; i++) {
                if (pt[i] == int.class) args[i] = ints[Math.min(2, k++)];
                else if (pt[i] == ContainerInput.class) args[i] = ContainerInput.QUICK_MOVE;
                else args[i] = mc.player;
            }
            clickMethod.invoke(mc.gameMode, args);
        } catch (Throwable ignored) {}
    }
}
