package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Rechner. Versteht k/m/b/t, z. B. "2.5m * 1.5 + 300k" - praktisch fuer Stack-, Shop- und Bau-Rechnungen. */
public class PoldiCalculatorScreen extends PoldiScreen {
    private static final int W = 192, CW = 36, CH = 22, G = 3;
    private static String keep = "";
    private static final List<String> history = new ArrayList<>();
    private final Screen parent;
    private int left, top;
    private EditBox input;

    public PoldiCalculatorScreen(Screen parent) {
        super(Component.literal("Rechner"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        left = width / 2 - W / 2;
        top = height / 2 - 112;
        input = new EditBox(font, left, top, W, 20, Component.empty());
        input.setMaxLength(80);
        input.setValue(keep);
        input.setResponder(s -> keep = s);
        addRenderableWidget(input);
        setInitialFocus(input);
        String[][] rows = {
            {"C", "<", "(", ")", "/"},
            {"7", "8", "9", "k", "*"},
            {"4", "5", "6", "m", "-"},
            {"1", "2", "3", "b", "+"},
            {"0", ".", "^", "=", "Kopie"},
        };
        int y0 = top + 52;
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length; c++) {
                final String key = rows[r][c];
                button(() -> key.equals("<") ? "⌫" : key, () -> press(key), left + c * (CW + G), y0 + r * (CH + G), CW, CH, !key.matches("[0-9.]"));
            }
    }

    private void press(String k) {
        String t = input.getValue();
        switch (k) {
            case "C" -> input.setValue("");
            case "<" -> { if (!t.isEmpty()) input.setValue(t.substring(0, t.length() - 1)); }
            case "=" -> {
                Double v = PoldiCalc.eval(t);
                if (v != null) {
                    history.add(0, t + " = " + PoldiCalc.pretty(v));
                    while (history.size() > 4) history.remove(history.size() - 1);
                    input.setValue(PoldiCalc.plain(v));
                }
            }
            case "Kopie" -> {
                Double v = PoldiCalc.eval(t);
                if (v != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(PoldiCalc.plain(v));
                    PoldiClient.chat("Ergebnis kopiert: " + PoldiCalc.plain(v));
                }
            }
            default -> input.setValue(t + k);
        }
        setFocused(input);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        darkBackground(g, 0xE0);
        PoldiTheme.panel(g, left - 12, top - 24, W + 24, 236, 0xF0);
        PoldiTheme.gradientText(g, font, "Rechner", left, top - 16);
    }

    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Double v = PoldiCalc.eval(input.getValue());
        String main = v == null ? (input.getValue().isEmpty() ? "= 0" : "= …") : "= " + PoldiCalc.pretty(v);
        g.pose().pushMatrix();
        g.pose().translate(left + 2, top + 26);
        g.pose().scale(1.5f, 1.5f);
        PoldiTheme.text(g, font, main, 0, 0, v == null ? PoldiTheme.MUTED : PoldiTheme.GOOD);
        g.pose().popMatrix();
        if (v != null && Math.abs(v) >= 1000) {
            String sh = PoldiClient.fmt(Math.round(v));
            PoldiTheme.text(g, font, sh, left + W - font.width(sh) - 2, top + 26, PoldiTheme.SECOND);
        }
        int y = top + 52 + 5 * (CH + G) + 2;
        for (String h : history) { PoldiTheme.text(g, font, h, left + 2, y, PoldiTheme.MUTED); y += 10; }
    }

    @Override
    public void onClose() { open(parent); }
}
