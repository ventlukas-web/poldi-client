package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Button im roten Vanilla-Stil (eckig, Bevel-Kanten). Beschriftung kommt live aus einem Supplier. */
public class PoldiButton extends AbstractButton {
    private final Supplier<String> label;
    private final Runnable action;
    public boolean accent;
    /** Unsichtbare Klickflaeche (wird nicht gezeichnet). */
    public boolean hidden;

    public PoldiButton(int x, int y, int w, int h, Supplier<String> label, Runnable action, boolean accent) {
        super(x, y, w, h, Component.empty());
        this.label = label;
        this.action = action;
        this.accent = accent;
    }

    public String label() {
        try { return label.get(); } catch (Throwable t) { return ""; }
    }

    @Override
    public void onPress(InputWithModifiers input) { action.run(); }

    /** Normale Buttons: rotgrau. Hervorgehobene (accent): volle Hauptfarbe. */
    public static int baseColor(boolean accent) {
        if (!PoldiConfig.get().niceButtons) return accent ? 0xFF7A5050 : 0xFF6E6E6E;
        return accent ? PoldiTheme.shade(PoldiTheme.PRIMARY, 0.82f) : PoldiTheme.lerp(0xFF5E5252, PoldiTheme.PRIMARY, 0.22f);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (hidden) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = this.active && isHovered();
        int base = baseColor(accent);
        if (hov) base = PoldiTheme.lerp(base, PoldiTheme.PRIMARY, 0.45f);
        PoldiTheme.bevel(g, x, y, w, h, base, hov, this.active);
        Font f = Minecraft.getInstance().font;
        String s = label();
        int maxW = w - 8;
        if (f.width(PoldiTheme.ui(s)) > maxW) {
            while (s.length() > 1 && f.width(PoldiTheme.ui(s + "…")) > maxW) s = s.substring(0, s.length() - 1);
            s = s + "…";
        }
        Component t = PoldiTheme.ui(s);
        g.text(f, t, x + (w - f.width(t)) / 2, y + (h - 8) / 2, this.active ? PoldiTheme.TEXT : 0xFFA09090, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
