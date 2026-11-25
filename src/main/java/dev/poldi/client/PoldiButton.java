package dev.poldi.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Button mit der echten Minecraft-Button-Grafik. Beschriftung kommt live aus einem Supplier; "accent" = hervorgehoben (weisser Rand). */
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
        setOverrideRenderHighlightedSprite(() -> this.accent && this.active);
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
        String s = label();
        if (!s.equals(getMessage().getString())) setMessage(Component.literal(s));
        extractDefaultSprite(g);
        extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
