package dev.poldi.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Tastenbelegung aller Poldi-Funktionen (Klick auf die Taste, dann neue Taste druecken). */
public class PoldiKeysScreen extends PoldiScreen {
    private static final int W = 300;
    private static final int[][] RANGES = {
        {32, 32}, {39, 39}, {44, 57}, {59, 59}, {61, 61}, {65, 93}, {96, 96},
        {256, 269}, {280, 284}, {290, 314}, {320, 336}, {340, 348}
    };

    private final Screen parent;
    private int left, top;
    private int listening = -1;
    private long listenSince;

    public PoldiKeysScreen(Screen parent) {
        super(Component.literal("Tasten"));
        this.parent = parent;
    }

    static KeyMapping[] binds() {
        return new KeyMapping[]{PoldiClient.openMenu, PoldiClient.calcKey, PoldiClient.macroKey, PoldiClient.hudEditKey,
            PoldiClient.glassKey, PoldiClient.waypointKey, PoldiMisc.fullbrightKey, PoldiMisc.autoClickKey, PoldiMisc.zoomKey};
    }

    private static final String[] NAMES = {
        "Mod-Menü öffnen", "Rechner öffnen", "Sell-Makro Start/Stopp", "HUD-Editor öffnen", "Unsichtbares Glas an/aus",
        "Wegpunkt hier setzen", "Fullbright an/aus", "Autoclicker an/aus", "Zoom (halten)"
    };

    @Override
    protected void build() {
        left = width / 2 - W / 2;
        top = Math.max(26, (height - 300) / 2 + 20);
        KeyMapping[] kb = binds();
        for (int i = 0; i < kb.length; i++) {
            final int idx = i;
            final KeyMapping k = kb[i];
            int y = top + 30 + i * 22;
            button(() -> listening == idx ? "Drücke eine Taste …" : k.getTranslatedKeyMessage().getString(),
                () -> { listening = idx; listenSince = System.currentTimeMillis(); }, left + 150, y, 100, 20, listening == idx);
            button(() -> "Reset", () -> { k.setKey(k.getDefaultKey()); apply(); }, left + 254, y, 46, 20);
        }
        button(() -> "Zurück", this::onClose, left + W / 2 - 50, top + 30 + kb.length * 22 + 8, 100, 20);
    }

    private static void apply() {
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        darkBackground(g, 0xF0);
        PoldiTheme.panel(g, left - 12, top - 24, W + 24, 30 + binds().length * 22 + 56, 0xF0);
        PoldiTheme.gradientText(g, font, "Tasten", left, top - 16);
    }

    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (int i = 0; i < NAMES.length; i++) PoldiTheme.text(g, font, NAMES[i], left, top + 30 + i * 22 + 6, PoldiTheme.TEXT);
        String hint = listening >= 0 ? "Taste drücken (Rücktaste = Belegung entfernen)." : "Klick auf eine Taste, dann neue Taste drücken.";
        PoldiTheme.text(g, font, hint, left, top + 30 + NAMES.length * 22 + 36, PoldiTheme.MUTED);

        if (listening >= 0 && System.currentTimeMillis() - listenSince > 200) {
            long handle = Minecraft.getInstance().getWindow().handle();
            for (int[] r : RANGES) {
                for (int code = r[0]; code <= r[1]; code++) {
                    if (GLFW.glfwGetKey(handle, code) != GLFW.GLFW_PRESS) continue;
                    if (code == GLFW.GLFW_KEY_ESCAPE) { listening = -1; return; }
                    KeyMapping k = binds()[listening];
                    if (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE) k.setKey(InputConstants.UNKNOWN);
                    else k.setKey(InputConstants.Type.KEYSYM.getOrCreate(code));
                    apply();
                    listening = -1;
                    return;
                }
            }
        }
    }

    @Override
    public void onClose() { open(parent); }
}
