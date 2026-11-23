package dev.poldi.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ersetzt das Server-Scoreboard (rechts) durch eine anpassbare Version: Transparenz, Farben, Zahlen, Groesse. */
public final class PoldiScoreboard {
    private PoldiScoreboard() {}

    private static int rgb(String hex, int def) {
        Integer v = PoldiTheme.parseHex(hex);
        return v == null ? def : v;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker tick, Runnable vanilla) {
        long p0 = PoldiProf.begin();
        try { render0(g, vanilla); } catch (Throwable t) { vanilla.run(); } finally { PoldiProf.end("Scoreboard", p0); }
    }

    private static void render0(GuiGraphicsExtractor g, Runnable vanilla) {
        PoldiConfig cfg = PoldiConfig.get();
        if (!cfg.sbCustom) { vanilla.run(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.hud.isHidden()) return;
        Font f = mc.font;

        Component title;
        List<Component> names = new ArrayList<>();
        List<String> nums = new ArrayList<>();
        var sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj != null) {
            title = obj.getDisplayName();
            List<PlayerScoreEntry> es = new ArrayList<>();
            for (PlayerScoreEntry e : sb.listPlayerScores(obj)) if (!e.isHidden()) es.add(e);
            es.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
            for (int i = 0; i < Math.min(15, es.size()); i++) {
                PlayerScoreEntry e = es.get(i);
                names.add(PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName()));
                nums.add(String.valueOf(e.value()));
            }
        } else if (mc.gui.screen() instanceof PoldiHudEditor) {
            title = Component.literal("Scoreboard");
            for (int i = 1; i <= 4; i++) { names.add(Component.literal("Beispiel-Zeile " + i)); nums.add(String.valueOf(i * 10)); }
        } else {
            PoldiHud.BOUNDS.remove("scoreboard");
            return;
        }

        boolean th = cfg.sbTheme;
        int text = th ? 0xFFFFFFFF : 0xFF000000 | rgb(cfg.sbText, 0xFFFFFF);
        int bg = th ? PoldiTheme.alpha(PoldiTheme.BG, cfg.sbAlpha) : PoldiTheme.alpha(0xFF000000 | rgb(cfg.sbBg, 0x140606), cfg.sbAlpha);
        int border = th ? PoldiTheme.PRIMARY : 0xFF000000 | rgb(cfg.sbBorder, 0xDC2626);
        int pad = 4, rowH = 10;
        int w = f.width(title);
        for (int i = 0; i < names.size(); i++) w = Math.max(w, f.width(names.get(i)) + (cfg.sbNumbers ? 10 + f.width(nums.get(i)) : 0));
        int h = (names.size() + 1) * rowH + pad * 2 + 2;
        float sc = cfg.sbScale;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float[] pos = PoldiHud.place("scoreboard", sw - (w + pad * 2) * sc - 4, sh / 2f - h * sc / 2f, (w + pad * 2) * sc, h * sc, mc);
        g.pose().pushMatrix();
        g.pose().translate(pos[0], pos[1]);
        g.pose().scale(sc, sc);
        int fw = w + pad * 2;
        PoldiTheme.rect(g, 0, 0, fw, h, bg);
        if (cfg.sbBorderOn) PoldiTheme.outline(g, 0, 0, fw, h, border);
        PoldiTheme.text(g, f, title, (fw - f.width(title)) / 2, pad + 1, text);
        g.fill(pad, pad + rowH, fw - pad, pad + rowH + 1, PoldiTheme.alpha(border, 0x90));
        int y = pad + rowH + 3;
        for (int i = 0; i < names.size(); i++) {
            PoldiTheme.text(g, f, names.get(i), pad, y, text);
            if (cfg.sbNumbers) PoldiTheme.text(g, f, nums.get(i), fw - pad - f.width(nums.get(i)), y, th ? PoldiTheme.SECOND : 0xFFFF5555);
            y += rowH;
        }
        g.pose().popMatrix();
    }
}
