package dev.poldi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/** HUD-Editor: Elemente mit der Maus (linke Taste halten) verschieben. */
public class PoldiHudEditor extends PoldiScreen {
    private final Screen parent;
    private String dragId = null;
    private float grabDx, grabDy;
    private boolean wasDown = false;

    public PoldiHudEditor(Screen parent) {
        super(Component.literal("HUD-Editor"));
        this.parent = parent;
    }

    private static String nameOf(String id) {
        return switch (id) {
            case "info" -> "Info";
            case "keys" -> "Keystrokes";
            case "mods" -> "Module";
            case "waypoints" -> "Wegpunkte";
            case "scoreboard" -> "Scoreboard";
            case "custom" -> "Eigenes HUD";
            case "nearby" -> "Spieler";
            case "target" -> "Ziel";
            case "combo" -> "Combo";
            case "pvp" -> "PvP";
            case "pvpitems" -> "PvP-Items";
            case "mem" -> "System";
            case "vitals" -> "Status";
            case "armor" -> "Haltbarkeit";
            case "compass" -> "Kompass";
            case "sellstatus" -> "Sell Status";
            case "potions" -> "Effekte";
            case "session" -> "Session";
            case "notes" -> "Notizen";
            case "events" -> "Events";
            case "twitch" -> "Twitch";
            case "day" -> "Zeit";
            case "light" -> "Licht";
            case "pickup" -> "Pickup-Log";
            default -> id;
        };
    }

    @Override
    protected void build() {
        int cx = width / 2;
        button(() -> "Fertig", this::onClose, cx - 104, height - 28, 100, 20, true);
        button(() -> "Zurücksetzen", () -> { PoldiConfig c = PoldiConfig.get(); c.hudPos.clear(); c.save(); }, cx + 4, height - 28, 100, 20);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x50000000);
    }

    private boolean overButton(int mx, int my) {
        for (PoldiButton b : btns) if (b.isMouseOver(mx, my)) return true;
        return false;
    }

    @Override
    protected void extra(GuiGraphicsExtractor g, int mx, int my) {
        long handle = Minecraft.getInstance().getWindow().handle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        PoldiConfig cfg = PoldiConfig.get();

        if (down && !wasDown && !overButton(mx, my)) {
            String hit = null;
            for (Map.Entry<String, float[]> e : PoldiHud.BOUNDS.entrySet()) {
                float[] b = e.getValue();
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) { hit = e.getKey(); grabDx = mx - b[0]; grabDy = my - b[1]; }
            }
            dragId = hit;
        } else if (down && dragId != null) {
            float[] b = PoldiHud.BOUNDS.get(dragId);
            if (b != null) {
                float nx = Math.max(0, Math.min(width - b[2], mx - grabDx));
                float ny = Math.max(0, Math.min(height - b[3], my - grabDy));
                cfg.hudPos.put(dragId, new float[]{nx / width, ny / height});
            }
        } else if (!down && wasDown && dragId != null) {
            cfg.save();
            dragId = null;
        }
        wasDown = down;

        for (Map.Entry<String, float[]> e : PoldiHud.BOUNDS.entrySet()) {
            float[] b = e.getValue();
            boolean active = e.getKey().equals(dragId);
            boolean hov = mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
            int col = active ? PoldiTheme.TEXT : (hov ? PoldiTheme.SECOND : PoldiTheme.alpha(PoldiTheme.PRIMARY, 0xE0));
            PoldiTheme.outline(g, (int) b[0] - 2, (int) b[1] - 2, (int) b[2] + 4, (int) b[3] + 4, col);
            PoldiTheme.text(g, font, nameOf(e.getKey()), (int) b[0], (int) b[1] - 12, col);
        }
        String t1 = PoldiHud.BOUNDS.isEmpty()
            ? "Keine HUD-Elemente aktiv – schalte sie im Menü ein."
            : "Linke Maustaste halten und ziehen, um ein Element zu verschieben.";
        PoldiTheme.text(g, font, t1, (width - font.width(t1)) / 2, height / 2 - 4, PoldiTheme.TEXT);
    }

    @Override
    public void onClose() { open(parent); }
}
