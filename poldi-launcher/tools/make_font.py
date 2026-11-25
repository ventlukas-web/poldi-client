# Baut res/PoldiPixel.ttf: Pixel-Schrift aus DejaVu Sans (ohne Kantenglaettung gerastert) + eigene Symbole.
from PIL import Image, ImageDraw, ImageFont
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont
import sys
F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10)
U = 100
chars = [chr(c) for c in range(33, 127)] + list("äöüÄÖÜß€·…–—„“”‘’«»°§×÷²³µ¼½éèêáàâíóúñçÉ")
# Eigene Symbole: Zeilen von oben, '#' = Pixel; letzte Zeile liegt auf der Grundlinie
CUSTOM = {
    "−": ["", "", "", "#####", "", ""],
    "―": ["", "", "", "########", "", ""],
    "▸": ["#", "##", "###", "##", "#", ""],
    "▾": ["", "#####", " ### ", "  #  ", "", ""],
    "✓": ["     #", "    # ", "#  #  ", " ##   ", " #    ", ""],
    "✕": ["#   #", " # # ", "  #  ", " # # ", "#   #", ""],
}
def pen(rects):
    p = TTGlyphPen(None)
    for x0, y0, x1, y1 in rects:
        p.moveTo((x0, y0)); p.lineTo((x0, y1)); p.lineTo((x1, y1)); p.lineTo((x1, y0)); p.closePath()
    return p.glyph()
order = [".notdef", "space"]; glyphs = {".notdef": pen([(0, 0, 500, 700)]), "space": pen([])}
adv = {".notdef": 600, "space": 4 * U}; cmap = {32: "space"}
im = Image.new("1", (20, 16)); d = ImageDraw.Draw(im); d.fontmode = "1"; d.text((2, 1), "H", font=F, fill=1)
base = max(y for y in range(16) if any(im.getpixel((x, y)) for x in range(20))) + 1
def add(ch, rows):  # rows: Liste von Pixel-Zeilen (oben->unten), letzte = unterste Zeile ueber Grundlinie 0
    rects = []; maxx = 0; n = len(rows)
    for i, r in enumerate(rows):
        x = 0
        while x < len(r):
            if r[x] == "#":
                s = x
                while x < len(r) and r[x] == "#": x += 1
                rects.append((s * U, (n - 1 - i) * U, x * U, (n - i) * U)); maxx = max(maxx, x)
            else: x += 1
    name = "uni%04X" % ord(ch); glyphs[name] = pen(rects); adv[name] = (maxx + 1) * U; cmap[ord(ch)] = name; order.append(name)
for ch in chars:
    im = Image.new("1", (24, 18)); d = ImageDraw.Draw(im); d.fontmode = "1"; d.text((4, 1), ch, font=F, fill=1)
    px = [(x, y) for y in range(18) for x in range(24) if im.getpixel((x, y))]
    if not px: continue
    x0 = min(p[0] for p in px)
    rows = ["".join("#" if im.getpixel((x, y)) else " " for x in range(x0, 24)) for y in range(0, base)]
    below = ["".join("#" if im.getpixel((x, y)) else " " for x in range(x0, 24)) for y in range(base, 18)]
    rects = []; maxx = 0
    for y in range(18):
        x = x0
        while x < 24:
            if im.getpixel((x, y)):
                s = x
                while x < 24 and im.getpixel((x, y)): x += 1
                rects.append(((s - x0) * U, (base - y - 1) * U, (x - x0) * U, (base - y) * U)); maxx = max(maxx, x - x0)
            else: x += 1
    name = "uni%04X" % ord(ch); glyphs[name] = pen(rects); adv[name] = (maxx + 1) * U; cmap[ord(ch)] = name; order.append(name)
for ch, rows in CUSTOM.items(): add(ch, rows)
fb = FontBuilder(1000, isTTF=True)
fb.setupGlyphOrder(order); fb.setupCharacterMap(cmap); fb.setupGlyf(glyphs)
fb.setupHorizontalMetrics({k: (adv[k], 0) for k in order})
fb.setupHorizontalHeader(ascent=1100, descent=-300)
fb.setupNameTable({"familyName": "Poldi Pixel", "styleName": "Regular"})
fb.setupOS2(sTypoAscender=1100, sTypoDescender=-300, usWinAscent=1100, usWinDescent=300)
fb.setupPost(); out = sys.argv[1] if len(sys.argv) > 1 else "PoldiPixel.ttf"; fb.save(out)
f = TTFont(out); n = f["name"]
for nid, val in [(1, "Poldi Pixel"), (2, "Regular"), (3, "PoldiPixel-Regular"), (4, "Poldi Pixel"), (6, "PoldiPixel-Regular")]:
    n.setName(val, nid, 3, 1, 0x409); n.setName(val, nid, 1, 0, 0)
f.save(out); print(len(order), "Zeichen")
