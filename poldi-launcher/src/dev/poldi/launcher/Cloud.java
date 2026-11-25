package dev.poldi.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/** Vanilla-Look: Pixel-Schrift, eckige Minecraft-Buttons mit Kanten, dunkle Flaechen, Netherrack-Hintergrund. */
public final class Cloud {
    private Cloud() {}
    public static final Color PURPLE = new Color(0xB0, 0x2E, 0x2E), BLUE = new Color(0xE0, 0x6A, 0x6A), GOOD = new Color(0x55, 0xFF, 0x55), BAD = new Color(0xFF, 0x55, 0x55);
    public static final Color TEXT = new Color(0xFF, 0xFF, 0xFF), MUTED = new Color(0xA0, 0xA0, 0xA0);
    /** Pixel-Pixelgroesse (wie GUI-Skalierung 2 in Minecraft). */
    public static final int PX = 2;

    private static Font PIXEL;
    static {
        try (var in = Cloud.class.getResourceAsStream("/PoldiPixel.ttf")) { if (in != null) PIXEL = Font.createFont(Font.TRUETYPE_FONT, in); } catch (Exception ignored) {}
    }
    /** Pixel-Schrift in ganzen Vielfachen (10/20/30), damit jeder Pixel scharf bleibt. */
    public static Font font(int style, float size) {
        float s = size <= 11.5f ? 10f : size >= 28f ? 30f : 20f;
        if (PIXEL == null) return new Font("Dialog", style, Math.round(size));
        return PIXEL.deriveFont(Font.PLAIN, s);
    }

    public static Color a(Color c, int al) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), al); }
    public static Color mix(Color x, Color y, float t) { return new Color((int) (x.getRed() + (y.getRed() - x.getRed()) * t), (int) (x.getGreen() + (y.getGreen() - x.getGreen()) * t), (int) (x.getBlue() + (y.getBlue() - x.getBlue()) * t)); }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Minecraft-Text mit Schatten (Schatten = 1/4 Helligkeit, um 1 Pixel versetzt). */
    public static void text(Graphics2D g, String s, int x, int y, Color c) {
        int o = Math.max(1, g.getFont().getSize() / 10);
        g.setColor(new Color(c.getRed() / 4, c.getGreen() / 4, c.getBlue() / 4, c.getAlpha())); g.drawString(s, x + o, y + o);
        g.setColor(c); g.drawString(s, x, y);
    }

    /** Netherrack-Kachel (16x16, eigene Pixel), fuer den Hintergrund. */
    public static java.awt.image.BufferedImage netherTile() {
        var t = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random r = new java.util.Random(26_2L);
        int[] pal = {0x5E1E1E, 0x6B2424, 0x7A2C2C, 0x532020, 0x8A3434, 0x4A1A1A, 0x6E2A26};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) t.setRGB(x, y, pal[r.nextInt(pal.length)]);
        for (int i = 0; i < 18; i++) { int x = r.nextInt(15), y = r.nextInt(16); t.setRGB(x, y, 0x3E1414); t.setRGB(x + 1, y, 0x3E1414); }
        return t;
    }

    /** Dunkle Vanilla-Flaeche (wie Listen/Menues), eckig. bumps/r werden ignoriert. */
    public static void card(Graphics2D g, float x, float y, float w, float h, float r, boolean bumps, int alpha) {
        int X = Math.round(x), Y = Math.round(y), W = Math.round(w), H = Math.round(h);
        g.setColor(new Color(0, 0, 0, 150)); g.fillRect(X, Y, W, H);
        g.setColor(Color.BLACK); g.fillRect(X, Y, W, PX); g.fillRect(X, Y + H - PX, W, PX); g.fillRect(X, Y, PX, H); g.fillRect(X + W - PX, Y, PX, H);
        g.setColor(new Color(255, 255, 255, 40)); g.fillRect(X + PX, Y + PX, W - 2 * PX, PX); g.fillRect(X + PX, Y + PX, PX, H - 2 * PX);
    }

    /** Vanilla-Button zeichnen (auch fuer andere Komponenten). */
    public static void bevel(Graphics2D g, int x, int y, int w, int h, Color base, boolean hover, boolean enabled) {
        Color fill = enabled ? base : new Color(0x2C, 0x2C, 0x2C);
        g.setColor(hover && enabled ? Color.WHITE : Color.BLACK); g.fillRect(x, y, w, h);
        g.setColor(fill); g.fillRect(x + PX, y + PX, w - 2 * PX, h - 2 * PX);
        if (enabled) {
            g.setColor(mix(fill, Color.WHITE, 0.32f)); g.fillRect(x + PX, y + PX, w - 2 * PX, PX); g.fillRect(x + PX, y + PX, PX, h - 3 * PX);
            g.setColor(mix(fill, Color.BLACK, 0.38f)); g.fillRect(x + PX, y + h - 2 * PX, w - 2 * PX, PX); g.fillRect(x + w - 2 * PX, y + 2 * PX, PX, h - 3 * PX);
        }
    }

    // ---------- Button ----------
    public static class Btn extends JComponent {
        String text; final Runnable action; boolean primary; boolean hover; boolean enabled = true;
        public void setPrimary(boolean p) { primary = p; repaint(); }
        public Btn(String text, boolean primary, Runnable action) {
            this.text = text; this.primary = primary; this.action = action; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); } public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { if (enabled && e.getButton() == MouseEvent.BUTTON1) action.run(); }
            });
        }
        public void setText(String t) { text = t; repaint(); }
        public void setEnabled(boolean b) { enabled = b; setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g);
            bevel(g, 0, 0, getWidth(), getHeight(), primary ? new Color(0x9E, 0x2A, 0x2A) : new Color(0x6F, 0x6F, 0x6F), hover, enabled);
            g.setFont(font(Font.PLAIN, primary && getHeight() >= 40 ? 30f : 20f)); FontMetrics fm = g.getFontMetrics();
            String t = text; while (t.length() > 1 && fm.stringWidth(t) > getWidth() - 12) t = t.substring(0, t.length() - 2) + "…";
            text(g, t, (getWidth() - fm.stringWidth(t)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1, enabled ? (hover ? new Color(0xFF, 0xFF, 0xA0) : TEXT) : MUTED);
            g.dispose();
        }
    }

    // ---------- Toggle: Beschriftung + kleiner Vanilla-Button AN/AUS ----------
    public static class Toggle extends JComponent {
        public boolean on; final String label; final java.util.function.Consumer<Boolean> change; boolean hover;
        public Toggle(String label, boolean on, java.util.function.Consumer<Boolean> change) {
            this.label = label; this.on = on; this.change = change; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { setOn(!Toggle.this.on); change.accept(Toggle.this.on); }
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); } public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        public void setOn(boolean v) { on = v; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g);
            g.setFont(font(Font.PLAIN, 14f)); FontMetrics fm = g.getFontMetrics();
            text(g, label, 0, (getHeight() + fm.getAscent() - fm.getDescent()) / 2, TEXT);
            int bw = 72, bh = Math.min(getHeight(), 28), x = getWidth() - bw, y = (getHeight() - bh) / 2;
            bevel(g, x, y, bw, bh, new Color(0x6F, 0x6F, 0x6F), hover, true);
            String t = on ? "AN" : "AUS";
            text(g, t, x + (bw - fm.stringWidth(t)) / 2, y + (bh + fm.getAscent() - fm.getDescent()) / 2 - 1, on ? GOOD : BAD);
            g.dispose();
        }
    }

    // ---------- Textfeld (wie Minecraft-Eingabefeld) ----------
    public static class Field extends JTextField {
        public Field(String text) {
            super(text); setOpaque(false); setForeground(Color.WHITE); setCaretColor(Color.WHITE); setFont(font(Font.PLAIN, 14f));
            setBorder(new EmptyBorder(4, 10, 4, 10));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g);
            g.setColor(hasFocus() ? Color.WHITE : new Color(0xA0, 0xA0, 0xA0)); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.BLACK); g.fillRect(PX, PX, getWidth() - 2 * PX, getHeight() - 2 * PX);
            g.dispose(); super.paintComponent(g0);
        }
    }

    /** Fortschrittsbalken wie der Minecraft-Ladebildschirm: weisser Rahmen, weisse Fuellung. */
    public static class Bar extends JComponent {
        public double v;
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g); int w = getWidth(), h = getHeight();
            g.setColor(new Color(0, 0, 0, 120)); g.fillRect(0, 0, w, h);
            g.setColor(Color.WHITE); g.fillRect(0, 0, w, PX); g.fillRect(0, h - PX, w, PX); g.fillRect(0, 0, PX, h); g.fillRect(w - PX, 0, PX, h);
            int fw = (int) ((w - 4 * PX) * Math.min(1, Math.max(0, v)));
            if (fw > 0) g.fillRect(2 * PX, 2 * PX, fw, h - 4 * PX);
            g.dispose();
        }
    }
}
