package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Auswahl, welche Items das Sell-Makro verkauft. */
public class PoldiSellScreen extends PoldiScreen {
    private static final int CW = 200, CELL = 18, MAX_SHOWN = 18;
    private final Screen parent;
    private int left, top, gx;
    private EditBox idField;

    private record Cell(int x, int y, ItemStack stack, boolean selected) {}
    private record Label(String text, int x, int y, int color) {}
    private final List<Cell> cells = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    public PoldiSellScreen(Screen parent) {
        super(Component.literal("Sell-Makro"));
        this.parent = parent;
    }

    private static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }

    private static ItemStack stackOf(String id) {
        Identifier i = Identifier.tryParse(id);
        if (i == null || !BuiltInRegistries.ITEM.containsKey(i)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.getValue(i));
    }

    private void toggleId(String id) {
        PoldiConfig c = PoldiConfig.get();
        if (!c.sellItems.remove(id)) c.sellItems.add(id);
        c.save();
        rebuildWidgets();
    }

    @Override
    protected void build() {
        cells.clear();
        labels.clear();
        PoldiConfig c = PoldiConfig.get();
        left = width / 2 - CW / 2;
        gx = left + (CW - 9 * CELL) / 2;
        top = Math.max(26, (height - 250) / 2 + 22);

        int y = top;
        button(() -> PoldiMacro.isRunning() ? "Stoppen" : "Starten", PoldiMacro::toggle, left, y, 96, 20, true);
        button(() -> "Auto: " + (c.sellAuto ? "AN" : "AUS"), () -> { c.sellAuto = !c.sellAuto; c.save(); }, left + 104, y, 96, 20);

        y += 28;
        int more = Math.max(0, c.sellItems.size() - MAX_SHOWN);
        labels.add(new Label("Gewählte Items (Klick = entfernen)" + (more > 0 ? "  +" + more : ""), left, y, PoldiTheme.MUTED));
        y += 11;
        List<String> ids = c.sellItems;
        if (ids.isEmpty()) {
            labels.add(new Label("Noch keine Items gewählt.", gx, y + 5, PoldiTheme.TEXT));
            y += CELL + 4;
        } else {
            int shown = Math.min(ids.size(), MAX_SHOWN);
            for (int i = 0; i < shown; i++) {
                final String iid = ids.get(i);
                int cx = gx + (i % 9) * CELL, cy = y + (i / 9) * CELL;
                cells.add(new Cell(cx, cy, stackOf(iid), true));
                hitbox(() -> toggleId(iid), cx, cy, CELL, CELL);
            }
            y += ((shown + 8) / 9) * CELL + 4;
        }

        labels.add(new Label("Dein Inventar (Klick = wählen / abwählen)", left, y, PoldiTheme.MUTED));
        y += 11;
        Minecraft mc = Minecraft.getInstance();
        Set<String> sel = new HashSet<>(c.sellItems);
        if (mc.player != null) {
            for (int r = 0; r < 4; r++) {
                int cy = y + r * CELL + (r == 3 ? 4 : 0);
                for (int col = 0; col < 9; col++) {
                    int slot = r == 3 ? col : 9 + r * 9 + col;
                    ItemStack st = mc.player.getInventory().getItem(slot);
                    int cx = gx + col * CELL;
                    if (st.isEmpty()) continue;
                    final String iid = id(st);
                    cells.add(new Cell(cx, cy, st, sel.contains(iid)));
                    hitbox(() -> toggleId(iid), cx, cy, CELL, CELL);
                }
            }
        }
        y += 4 * CELL + 10;

        idField = new EditBox(font, left, y, 90, 18, Component.empty());
        idField.setMaxLength(80);
        idField.setHint(Component.literal("z. B. wheat"));
        addRenderableWidget(idField);
        button(() -> "Hinzufügen", () -> {
            String t = idField.getValue().trim().toLowerCase();
            if (t.isEmpty()) return;
            String full = t.contains(":") ? t : "minecraft:" + t;
            if (stackOf(full).isEmpty()) { PoldiClient.chat("Unbekanntes Item: " + t); return; }
            if (!c.sellItems.contains(full)) { c.sellItems.add(full); c.save(); }
            rebuildWidgets();
        }, left + 94, y - 1, 62, 20);
        button(() -> "Hand", () -> {
            if (mc.player == null) return;
            ItemStack st = mc.player.getMainHandItem();
            if (st.isEmpty()) { PoldiClient.chat("Du hältst kein Item in der Hand."); return; }
            String iid = id(st);
            if (!c.sellItems.contains(iid)) { c.sellItems.add(iid); c.save(); }
            rebuildWidgets();
        }, left + 160, y - 1, 40, 20);

        y += 26;
        button(() -> "-", () -> { c.sellClickDelay = Math.max(0, c.sellClickDelay - 1); c.save(); }, left, y, 24, 20);
        button(() -> "Klick-Pause: " + c.sellClickDelay + " Tick" + (c.sellClickDelay == 1 ? "" : "s"), () -> {}, left + 28, y, 144, 20);
        button(() -> "+", () -> { c.sellClickDelay = Math.min(20, c.sellClickDelay + 1); c.save(); }, left + 176, y, 24, 20);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        darkBackground(g, 0xF0);
        PoldiTheme.panel(g, left - 14, top - 24, CW + 28, 262, 0xF0);
        PoldiTheme.gradientText(g, font, "Sell-Makro", left, top - 16);
    }

    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Label l : labels) PoldiTheme.text(g, font, l.text(), l.x(), l.y(), l.color());
        String hover = null;
        for (Cell c : cells) {
            boolean hov = mouseX >= c.x() && mouseX < c.x() + CELL && mouseY >= c.y() && mouseY < c.y() + CELL;
            PoldiTheme.rect(g, c.x(), c.y(), CELL - 1, CELL - 1, 0xE0201010);
            PoldiTheme.outline(g, c.x(), c.y(), CELL - 1, CELL - 1,
                hov ? PoldiTheme.TEXT : (c.selected() ? PoldiTheme.PRIMARY : PoldiTheme.alpha(PoldiTheme.MUTED, 0x50)));
            if (!c.stack().isEmpty()) g.item(c.stack(), c.x() + 1, c.y() + 1);
            if (hov && !c.stack().isEmpty()) hover = c.stack().getHoverName().getString();
        }
        PoldiTheme.text(g, font, hover != null ? hover : "Läuft nur mit gewählten Items. Menü voll → schließen → /sell",
            left, top + 226, hover != null ? PoldiTheme.TEXT : PoldiTheme.MUTED);
    }

    @Override
    public void onClose() { open(parent); }
}
