package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Basis fuer alle Poldi-Screens: Buttons im roten Vanilla-Stil. */
public abstract class PoldiScreen extends Screen {
    protected final List<PoldiButton> btns = new ArrayList<>();

    protected PoldiScreen(Component title) { super(title); }

    protected abstract void build();

    @Override
    protected final void init() {
        btns.clear();
        build();
    }

    protected PoldiButton button(Supplier<String> label, Runnable action, int x, int y, int w, int h) {
        return button(label, action, x, y, w, h, false);
    }

    protected PoldiButton button(Supplier<String> label, Runnable action, int x, int y, int w, int h, boolean accent) {
        PoldiButton b = new PoldiButton(x, y, w, h, label, action, accent);
        addRenderableWidget(b);
        btns.add(b);
        return b;
    }

    /** Unsichtbare Klickflaeche (z. B. fuer Item-Raster). */
    protected PoldiButton hitbox(Runnable action, int x, int y, int w, int h) {
        PoldiButton b = new PoldiButton(x, y, w, h, () -> "", action, false);
        b.hidden = true;
        addRenderableWidget(b);
        return b;
    }

    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        extra(g, mouseX, mouseY);
    }

    /** Standard-Hintergrund: dunkel mit leichtem Rotstich. */
    protected void darkBackground(GuiGraphicsExtractor g, int alpha) {
        g.fillGradient(0, 0, width, height,
            PoldiTheme.alpha(PoldiTheme.lerp(0xFF060303, PoldiTheme.PRIMARY, 0.10f), alpha),
            PoldiTheme.alpha(PoldiTheme.lerp(0xFF060303, PoldiTheme.PRIMARY, 0.04f), alpha));
    }

    protected static void open(Screen s) { Minecraft.getInstance().gui.setScreen(s); }

    @Override
    public boolean isPauseScreen() { return false; }
}
