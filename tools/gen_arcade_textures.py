#!/usr/bin/env python3
"""Generate every PNG (and .mcmeta) NeroArcade ships, deterministically, from one colour table.

Hardware textures are 16x16 per block face: flat fill + two value steps (body / body-light bevel,
body / body-dark recess), a third step only for the 1-px trim seam. No gradients, dithering or noise.
Emissive parts live on their own ``<part>_glow.png`` overlay with a transparent background.

Screen sprite sheets (``textures/screen/<theme>/*.png``) are INDEXED PNGs: the palette is the theme's
sixteen colours, index 0 transparent, fixed index roles (1 bg, 2 floor, 3 wall, 4 wall-light, 5 exit,
6 pawn, 7 pawn-shade, 8 hazard, 9 hazard-hot, 10 signature, 11 pickup-2, 12 text, 13 text-dim,
14 flash, 15 danger). Sprites are 8x8; pawns and hazards have two frames, pickups and tiles one.
The renderer looks colours up by index, which is what makes a THEME chip a pure palette swap.

The bitmap font ``textures/screen/font/arcade5x7.png`` is 5x7 glyphs, 16 per row, ASCII 32..95, white
on transparent (the renderer tints it by palette index).

Every sprite here is an original Neroland object; nothing resembles a commercial game character.

usage: python3 tools/gen_arcade_textures.py [--check] [--only NAME[,NAME...]]
  (default) write every texture + mcmeta under common/src/main/resources/assets/neroarcade/textures
  --check   write nothing; regenerate in memory and fail (exit 1) on any missing, different-sized or
            pixel-different PNG, any missing .mcmeta, and any PNG under textures/ this script does
            not own.
"""
import os
import sys
import json

sys.dont_write_bytecode = True

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.stderr.write("Pillow is required: pip install pillow\n")
    sys.exit(2)

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets", "neroarcade")
TEX = os.path.join(ASSETS, "textures")

# ---------------------------------------------------------------------------------------------------
# Colour table. Mirrors client/ArcadeColours byte-for-byte (one "NAME": "#RRGGBB" per line).
# ---------------------------------------------------------------------------------------------------
PALETTE = {
    "BODY": "#141a28",
    "BODY_DARK": "#0b0f1a",
    "BODY_LIGHT": "#2a3140",
    "TRIM": "#3a4458",
    "INK": "#05070d",
    "TEXT_LIGHT": "#e8ecf4",
    "TEXT_SUBTLE": "#8a93a6",
    "NEON": "#ff3fa4",
    "CYAN": "#2fe3ff",
    "AMBER": "#ffb347",
    "DENY_RED": "#ff5c5c",
    "OK_GREEN": "#58e07a",
    "NO_POWER": "#7a2020",
    "STATUS_OFF": "#3a4458",
}

SLOT_COLOURS = {
    "arena": "#2fe3ff",
    "pawn": "#ff3fa4",
    "rule": "#ffb347",
    "hazard": "#ff5c5c",
    "pickup": "#58e07a",
    "control": "#e8ecf4",
    "pace": "#b48cff",
    "modifier": "#8f62ff",
    "multiplayer": "#22b8d8",
    "theme": "#ff8ccf",
}

SLOT_GLYPHS = {
    "arena": "#####/#...#/#...#/#...#/#####",
    "pawn": "..#../.###./..#../.#.#./#...#",
    "rule": "#####/#..##/#.#.#/##..#/#####",
    "hazard": "..#../..#../.#.#./.#.#./#####",
    "pickup": "..#../.###./#####/.###./..#..",
    "control": "..#../..#../#####/..#../..#..",
    "pace": "#..../##.../###../##.../#....",
    "modifier": "...../#.#../#.#.#/..#.#/.....",
    "multiplayer": ".#.#./###.#/.#.#./#.###/.#.#.",
    "theme": "..#../.###./#####/#####/.###.",
}

# Theme palettes: index 0 is transparent; 1..15 as in the role table above.
THEMES = {
    "neon_void": ["#05070d", "#0b0f1a", "#2a3140", "#3a4458", "#2fe3ff", "#ff3fa4", "#b0206e",
                  "#ff5c5c", "#ff9a3c", "#ff3fa4", "#58e07a", "#e8ecf4", "#8a93a6", "#ffffff", "#ff2020"],
    "hangar": ["#1a1d22", "#262b30", "#5b6670", "#8e98a4", "#ffce2c", "#7fd7ff", "#3fa9f5",
               "#e04545", "#ff8a8a", "#7fd7ff", "#55d55a", "#e8ecf4", "#8e98a4", "#ffffff", "#ff2020"],
    "cindara_dusk": ["#1c0a14", "#2a1020", "#6b2a2a", "#a04030", "#ffd27a", "#ffb347", "#c66a1a",
                     "#ff5c3c", "#ff9a1e", "#ff7a3c", "#ffe08a", "#ffe8d0", "#b08a7a", "#fff4e0", "#ff2a2a"],
    "terminal_green": ["#031006", "#06200c", "#0f5a1f", "#1f8a33", "#9dffb0", "#5fe36a", "#2fa83c",
                       "#e0ff5c", "#ffffff", "#58e07a", "#c8ff6a", "#9dffb0", "#3f9a4f", "#e8ffe8", "#ff4040"],
}


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def C(name):
    return rgb(PALETTE[name])


CLEAR = (0, 0, 0, 0)


class Img:
    """Tiny RGBA painter around a Pillow image."""

    def __init__(self, w=16, h=16, fill=CLEAR):
        self.im = Image.new("RGBA", (w, h), fill)
        self.px = self.im.load()
        self.w, self.h = w, h

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        """Fill [x0,x1) x [y0,y1)."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        """1-px outline of [x0,x1) x [y0,y1)."""
        self.rect(x0, y0, x1, y0 + 1, c)
        self.rect(x0, y1 - 1, x1, y1, c)
        self.rect(x0, y0, x0 + 1, y1, c)
        self.rect(x1 - 1, y0, x1, y1, c)

    def bevel(self, x0, y0, x1, y1, light, dark):
        """Top/left light step, bottom/right dark step (1 px)."""
        self.rect(x0, y0, x1, y0 + 1, light)
        self.rect(x0, y0, x0 + 1, y1, light)
        self.rect(x0, y1 - 1, x1, y1, dark)
        self.rect(x1 - 1, y0, x1, y1, dark)

    def glyph(self, rows, x, y, c, lit="#"):
        """Draw a '/'-separated pattern of '#' cells at (x, y)."""
        for dy, row in enumerate(rows.split("/")):
            for dx, ch in enumerate(row):
                if ch == lit:
                    self.put(x + dx, y + dy, c)


# ---------------------------------------------------------------------------------------------------
# Hardware painters (16x16 RGBA unless stated).  Each returns an Img.
# ---------------------------------------------------------------------------------------------------

def body_plain():
    im = Img(fill=C("BODY"))
    im.bevel(0, 0, 16, 16, C("BODY_LIGHT"), C("BODY_DARK"))
    return im


def body_light():
    im = Img(fill=C("BODY_LIGHT"))
    im.bevel(0, 0, 16, 16, C("TRIM"), C("BODY"))
    return im


def body_dark():
    im = Img(fill=C("BODY_DARK"))
    im.bevel(0, 0, 16, 16, C("BODY"), C("INK"))
    return im


def trim():
    im = Img(fill=C("BODY"))
    im.rect(0, 0, 16, 1, C("TRIM"))
    im.rect(0, 15, 16, 16, C("TRIM"))
    im.rect(0, 7, 16, 8, C("BODY_DARK"))
    return im


def ink():
    im = Img(fill=C("INK"))
    im.frame(0, 0, 16, 16, C("BODY_DARK"))
    return im


def cabinet_deck():
    im = Img(fill=C("BODY_LIGHT"))
    im.bevel(0, 0, 16, 16, C("TRIM"), C("BODY"))
    # label strip under the controls
    im.rect(3, 13, 13, 14, C("BODY"))
    return im


def cabinet_pad():
    im = Img(fill=C("BODY_LIGHT"))
    im.frame(0, 0, 16, 16, C("TRIM"))
    im.rect(6, 6, 10, 10, C("BODY"))
    return im


def button_glow(hexc):
    im = Img(fill=rgb(hexc))
    r, g, b, _ = rgb(hexc)
    light = (min(255, r + 40), min(255, g + 40), min(255, b + 40), 255)
    im.rect(0, 0, 16, 2, light)
    im.rect(0, 0, 2, 16, light)
    return im


def cabinet_slot():
    im = body_plain()
    im.frame(4, 6, 12, 10, C("AMBER"))
    im.rect(5, 7, 11, 9, C("INK"))
    return im


def cabinet_cartridge_stub():
    im = Img(fill=C("BODY_DARK"))
    im.frame(0, 0, 16, 16, C("TRIM"))
    im.rect(2, 4, 14, 7, C("TEXT_SUBTLE"))       # neutral band (theme tint is not block-state data)
    for x in range(2, 14, 2):
        im.put(x, 14, C("AMBER"))                  # contact pins
    return im


def cabinet_vent():
    im = body_plain()
    for y in (4, 7, 10):
        im.rect(3, y, 13, y + 1, C("INK"))
        im.rect(3, y + 1, 13, y + 2, C("BODY_DARK"))
    return im


def bus_port_glow():
    im = Img(fill=C("CYAN"))
    im.rect(6, 6, 10, 10, C("INK"))
    return im


def status_glow(hexc):
    """16x16 overlay: the 10x1 strip lives at row 1, x 3..13; everything else transparent.
    (A literal 16x1 texture would drop the whole block atlas to mip level 0, so the strip is padded
    to 16x16 and the model face UVs the row.)"""
    im = Img()
    im.rect(3, 1, 13, 2, rgb(hexc))
    return im


def marquee_frame():
    im = Img(fill=C("BODY"))
    im.bevel(0, 0, 16, 12, C("BODY_LIGHT"), C("BODY_DARK"))
    im.rect(0, 12, 16, 16, C("BODY_DARK"))
    im.frame(1, 12, 15, 16, C("AMBER"))   # x 1..15: the upper body's marquee face UVs that span
    return im


def marquee_glow_strip():
    """8 frames of 16x16 stacked vertically (16x128): magenta shimmer in rows 13..15, x 2..14."""
    im = Img(16, 128)
    neon = C("NEON")
    hot = (255, 120, 196, 255)
    for f in range(8):
        oy = f * 16
        im.rect(2, oy + 13, 14, oy + 15, neon)
        # a travelling hot pixel pair, period 8
        x = 2 + (f * 3) % 12
        im.put(x, oy + 13, hot)
        im.put((x + 6 - 2) % 12 + 2, oy + 14, hot)
    return im


def neon_glow():
    im = Img(fill=C("NEON"))
    im.rect(7, 0, 9, 16, (255, 120, 196, 255))
    return im


def cable_glow_strip():
    """4 frames (16x64): cyan cross on transparent, pulsing with a travelling bright pixel."""
    im = Img(16, 64)
    cyan = C("CYAN")
    dim = (24, 160, 190, 255)
    hot = (200, 250, 255, 255)
    for f in range(4):
        oy = f * 16
        c = cyan if f % 2 == 0 else dim
        im.rect(0, oy + 8, 16, oy + 9, c)
        im.rect(8, oy, 9, oy + 16, c)
        im.put((f * 4) % 16, oy + 8, hot)
        im.put(8, oy + (f * 4) % 16, hot)
    return im


def marquee_idle():
    """48x32 static idle card: NERO / ARCADE in the 5x7 font, magenta rule, cyan ticks."""
    im = Img(48, 32, C("BODY_DARK"))
    im.frame(0, 0, 48, 32, C("TRIM"))
    draw_text(im, "NERO", 12, 5, C("TEXT_LIGHT"))
    draw_text(im, "ARCADE", 6, 15, C("TEXT_LIGHT"))
    im.rect(6, 25, 42, 26, C("NEON"))
    for x in (2, 45):
        im.rect(x, 2, x + 1, 5, C("CYAN"))
        im.rect(x, 27, x + 1, 30, C("CYAN"))
    return im


def bench_top():
    im = body_plain()
    im.frame(1, 1, 15, 15, C("TRIM"))
    return im


def bench_holo_glow():
    im = Img()
    im.rect(2, 2, 14, 14, C("BODY_DARK"))
    im.frame(2, 2, 14, 14, C("CYAN"))
    im.rect(7, 7, 9, 9, C("CYAN"))
    return im


def bench_tray():
    im = body_plain()
    im.frame(3, 5, 13, 11, C("AMBER"))
    im.rect(4, 6, 12, 10, C("INK"))
    return im


def bench_console():
    im = body_light()
    for x in (4, 7, 10):
        im.rect(x, 6, x + 2, 8, C("BODY"))
        im.put(x, 6, C("TEXT_SUBTLE"))
    im.rect(3, 11, 13, 12, C("BODY"))
    return im


def bus_hub():
    im = body_plain()
    im.frame(1, 1, 15, 15, C("TRIM"))
    im.rect(6, 6, 10, 10, C("INK"))
    im.frame(5, 5, 11, 11, C("BODY_DARK"))
    return im


def bus_hub_glow():
    im = Img()
    im.rect(2, 7, 14, 9, C("CYAN"))
    im.rect(7, 2, 9, 14, C("CYAN"))
    return im


def cable_sheath():
    im = Img(fill=C("BODY_DARK"))
    im.frame(0, 0, 16, 16, C("INK"))
    im.rect(0, 8, 16, 9, (24, 160, 190, 255))   # static (non-emissive) data stripe
    im.rect(8, 0, 9, 16, (24, 160, 190, 255))
    return im


def duplicator_deck():
    im = body_plain()
    im.rect(0, 4, 16, 5, C("TRIM"))
    im.rect(2, 9, 14, 12, C("BODY_DARK"))
    return im


def duplicator_top():
    im = body_plain()
    im.frame(2, 1, 7, 5, C("AMBER"))           # original slot
    im.rect(3, 2, 6, 4, C("INK"))
    im.frame(9, 1, 14, 5, C("TEXT_SUBTLE"))    # blank slot
    im.rect(10, 2, 13, 4, C("INK"))
    im.rect(4, 6, 12, 14, C("BODY_DARK"))      # platter well
    return im


def duplicator_platter():
    im = Img(fill=C("BODY"))
    im.rect(1, 3, 15, 13, C("BODY_LIGHT"))
    im.rect(3, 1, 13, 15, C("BODY_LIGHT"))
    im.rect(2, 2, 14, 14, C("BODY_LIGHT"))
    im.rect(7, 7, 9, 9, C("BODY_DARK"))
    for (x, y) in ((8, 1), (8, 14), (1, 8), (14, 8)):
        im.put(x, y, (24, 160, 190, 255))
    return im


def duplicator_hood():
    im = body_light()
    im.rect(0, 7, 16, 8, C("TRIM"))
    return im


def exchange_pedestal():
    im = body_plain()
    im.rect(4, 0, 5, 16, C("TRIM"))
    im.rect(11, 0, 12, 16, C("TRIM"))
    im.rect(0, 12, 16, 14, C("INK"))
    return im


def exchange_keypad():
    im = body_light()
    for yy in (3, 6, 9, 12):
        for xx in (4, 7, 10):
            im.rect(xx, yy, xx + 2, yy + 2, C("BODY"))
    return im


def exchange_header_glow():
    im = Img()
    im.rect(2, 12, 14, 14, C("NEON"))
    im.rect(2, 12, 14, 13, (255, 120, 196, 255))
    return im


def exchange_screen():
    im = Img(fill=C("INK"))
    im.frame(0, 0, 16, 16, C("BODY_DARK"))
    im.frame(1, 1, 15, 15, C("TRIM"))
    return im


# ---- items ----------------------------------------------------------------------------------------

def chip_base():
    """10x15 keycard-shaped slab at x 3..12, rows 0..14; 2-px notch top-left; amber pins row 14;
    dark 7x7 glyph square at x 6..12, rows 7..13 (the band goes at rows 2..4, stack band at row 6)."""
    im = Img()
    im.rect(3, 0, 13, 15, C("BODY_LIGHT"))
    im.frame(3, 0, 13, 15, C("TRIM"))
    im.rect(3, 0, 5, 2, CLEAR)                 # orientation notch
    im.put(5, 0, C("TRIM"))
    im.put(3, 2, C("TRIM"))
    im.put(4, 1, C("TRIM"))
    im.rect(6, 7, 13, 14, C("BODY_DARK"))      # glyph square
    for x in (4, 6, 8, 10):
        im.put(x, 14, C("AMBER"))
        im.put(x + 1, 14, C("AMBER"))
    return im


def chip_band():
    im = Img()
    im.rect(3, 2, 13, 5, (255, 255, 255, 255))
    return im


def chip_band_stack():
    im = Img()
    im.rect(3, 6, 13, 7, (255, 255, 255, 255))
    return im


def chip_glyph(slot):
    im = Img()
    im.glyph(SLOT_GLYPHS[slot], 7, 8, (255, 255, 255, 255))
    return im


def cartridge_base():
    """12x15 slab at x 2..13, rows 0..14; amber contact edge; fixed decorative data glyph."""
    im = Img()
    im.rect(2, 0, 14, 15, C("BODY"))
    im.frame(2, 0, 14, 15, C("TRIM"))
    im.rect(2, 13, 14, 14, C("BODY_DARK"))
    for x in range(3, 13, 2):
        im.put(x, 14, C("AMBER"))
        im.put(x + 1, 14, C("BODY_DARK"))
    im.glyph("##.#/#..#/.##./#.##", 3, 9, C("CYAN"))
    return im


def cartridge_band():
    im = Img()
    im.rect(2, 2, 14, 5, (255, 255, 255, 255))
    return im


def cartridge_label():
    im = Img()
    im.rect(4, 6, 12, 8, C("TEXT_LIGHT"))
    return im


def cartridge_rev():
    im = Img()
    im.rect(11, 0, 14, 3, C("AMBER"))
    im.put(12, 1, C("BODY_DARK"))
    return im


# ---------------------------------------------------------------------------------------------------
# Bitmap font 5x7, ASCII 32..95, 16 glyphs per row -> 80x28.
# ---------------------------------------------------------------------------------------------------
FONT = {
    ' ': ".....|.....|.....|.....|.....|.....|.....",
    '!': "..#..|..#..|..#..|..#..|..#..|.....|..#..",
    '"': ".#.#.|.#.#.|.#.#.|.....|.....|.....|.....",
    '#': ".#.#.|.#.#.|#####|.#.#.|#####|.#.#.|.#.#.",
    '$': "..#..|.####|#.#..|.###.|..#.#|####.|..#..",
    '%': "##...|##..#|...#.|..#..|.#...|#..##|...##",
    '&': ".##..|#..#.|#..#.|.##..|#..#.|#...#|.###.",
    "'": "..#..|..#..|.#...|.....|.....|.....|.....",
    '(': "...#.|..#..|.#...|.#...|.#...|..#..|...#.",
    ')': ".#...|..#..|...#.|...#.|...#.|..#..|.#...",
    '*': ".....|#.#.#|.###.|#####|.###.|#.#.#|.....",
    '+': ".....|..#..|..#..|#####|..#..|..#..|.....",
    ',': ".....|.....|.....|.....|..##.|..#..|.#...",
    '-': ".....|.....|.....|#####|.....|.....|.....",
    '.': ".....|.....|.....|.....|.....|.##..|.##..",
    '/': "....#|...#.|...#.|..#..|.#...|.#...|#....",
    '0': ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.",
    '1': "..#..|.##..|..#..|..#..|..#..|..#..|.###.",
    '2': ".###.|#...#|....#|...#.|..#..|.#...|#####",
    '3': "#####|...#.|..#..|...#.|....#|#...#|.###.",
    '4': "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.",
    '5': "#####|#....|####.|....#|....#|#...#|.###.",
    '6': "..##.|.#...|#....|####.|#...#|#...#|.###.",
    '7': "#####|....#|...#.|..#..|.#...|.#...|.#...",
    '8': ".###.|#...#|#...#|.###.|#...#|#...#|.###.",
    '9': ".###.|#...#|#...#|.####|....#|...#.|.##..",
    ':': ".....|.##..|.##..|.....|.##..|.##..|.....",
    ';': ".....|.##..|.##..|.....|.##..|..#..|.#...",
    '<': "...#.|..#..|.#...|#....|.#...|..#..|...#.",
    '=': ".....|.....|#####|.....|#####|.....|.....",
    '>': ".#...|..#..|...#.|....#|...#.|..#..|.#...",
    '?': ".###.|#...#|....#|...#.|..#..|.....|..#..",
    '@': ".###.|#...#|....#|.##.#|#.#.#|#.#.#|.###.",
    'A': ".###.|#...#|#...#|#####|#...#|#...#|#...#",
    'B': "####.|#...#|#...#|####.|#...#|#...#|####.",
    'C': ".###.|#...#|#....|#....|#....|#...#|.###.",
    'D': "###..|#..#.|#...#|#...#|#...#|#..#.|###..",
    'E': "#####|#....|#....|####.|#....|#....|#####",
    'F': "#####|#....|#....|####.|#....|#....|#....",
    'G': ".###.|#...#|#....|#.###|#...#|#...#|.####",
    'H': "#...#|#...#|#...#|#####|#...#|#...#|#...#",
    'I': ".###.|..#..|..#..|..#..|..#..|..#..|.###.",
    'J': "..###|...#.|...#.|...#.|...#.|#..#.|.##..",
    'K': "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#",
    'L': "#....|#....|#....|#....|#....|#....|#####",
    'M': "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#",
    'N': "#...#|#...#|##..#|#.#.#|#..##|#...#|#...#",
    'O': ".###.|#...#|#...#|#...#|#...#|#...#|.###.",
    'P': "####.|#...#|#...#|####.|#....|#....|#....",
    'Q': ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#",
    'R': "####.|#...#|#...#|####.|#.#..|#..#.|#...#",
    'S': ".####|#....|#....|.###.|....#|....#|####.",
    'T': "#####|..#..|..#..|..#..|..#..|..#..|..#..",
    'U': "#...#|#...#|#...#|#...#|#...#|#...#|.###.",
    'V': "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
    'W': "#...#|#...#|#...#|#.#.#|#.#.#|##.##|#...#",
    'X': "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
    'Y': "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..",
    'Z': "#####|....#|...#.|..#..|.#...|#....|#####",
    '[': ".###.|.#...|.#...|.#...|.#...|.#...|.###.",
    '\\': "#....|.#...|.#...|..#..|...#.|...#.|....#",
    ']': ".###.|...#.|...#.|...#.|...#.|...#.|.###.",
    '^': "..#..|.#.#.|#...#|.....|.....|.....|.....",
    '_': ".....|.....|.....|.....|.....|.....|#####",
}


def draw_text(im, text, x, y, colour):
    for ch in text.upper():
        rows = FONT.get(ch, FONT['?']).split("|")
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c == "#":
                    im.put(x + dx, y + dy, colour)
        x += 6


def font_sheet():
    im = Img(80, 28)
    for code in range(32, 96):
        i = code - 32
        gx, gy = (i % 16) * 5, (i // 16) * 7
        rows = FONT[chr(code)].split("|")
        assert len(rows) == 7 and all(len(r) == 5 for r in rows), chr(code)
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c == "#":
                    im.put(gx + dx, gy + dy, (255, 255, 255, 255))
    return im


# ---------------------------------------------------------------------------------------------------
# Screen sprites: 8x8, chars are palette indices 0-9 a-f, '.' = 0 (transparent).
# ---------------------------------------------------------------------------------------------------
def S(*rows):
    assert len(rows) == 8 and all(len(r) == 8 for r in rows), rows
    return rows


PAWNS = [  # chip registration order: cursor, serpent, runner, paddle, ship; two frames each
    ("cursor", [
        S("........", ".66666..", ".6...6..", ".6.e.6..", ".6...6..", ".66666..", "........", "........"),
        S("........", ".66666..", ".6...6..", ".6.6.6..", ".6...6..", ".66666..", "........", "........"),
    ]),
    ("serpent", [  # frame 0 = head (visor in 5), frame 1 = body segment
        S("........", "..6666..", ".666666.", ".556666.", ".666666.", ".667766.", "..6776..", "........"),
        S("........", "..6666..", ".666666.", ".666666.", ".666776.", ".666776.", "..6666..", "........"),
    ]),
    ("runner", [
        S("..666...", "..6e6...", ".76666..", ".7.66...", "..66....", "..6.6...", "..6.6...", "..7.7..."),
        S("..666...", "..6e6...", ".76666..", ".7.66...", "..66....", "..6.6...", "..66....", "..7....."),
    ]),
    ("paddle", [
        S("........", "........", "........", "76666667", "76666667", "........", "........", "........"),
        S("........", "........", "........", "766ee667", "766ee667", "........", "........", "........"),
    ]),
    ("ship", [
        S("...66...", "...66...", "..6666..", ".66..66.", ".6.66.6.", "66.66.66", "...77...", "..9..8.."),
        S("...66...", "...66...", "..6666..", ".66..66.", ".6.66.6.", "66.66.66", "...77...", "..8..9.."),
    ]),
]

HAZARDS = [  # falling_blocks, bouncing_ball, chaser, laser_sweep, shrinking_floor
    ("falling_block", [
        S("f8888888", "88999988", "89888898", "89888898", "89888898", "89888898", "88999988", "88888888"),
        S("98888888", "88999988", "89888898", "89888898", "89888898", "89888898", "88999988", "88888888"),
    ]),
    ("bouncing_ball", [
        S("........", "..9999..", ".988889.", ".988889.", ".988889.", ".988889.", "..9999..", "........"),
        S("........", "........", "..99999.", ".9888889", ".9888889", ".9888889", "..99999.", "........"),
    ]),
    ("chaser", [
        S("8.....8.", ".88888..", "8888888.", "88f8888.", "8888888.", ".88888..", "8.....8.", "........"),
        S("8.....8.", ".88888..", "8888888.", "8888f88.", "8888888.", ".88888..", "8.....8.", "........"),
    ]),
    ("laser_sweep", [
        S("..8998..", "..8998..", "..8998..", "..8998..", "..8998..", "..8998..", "..8998..", "..8998.."),
        S("...88...", "...88...", "...88...", "...88...", "...88...", "...88...", "...88...", "...88..."),
    ]),
    ("shrinking_floor", [
        S("44444444", "44444444", "44444444", "44444444", "44444444", "44444444", "44444444", "44444444"),
        S(".444444.", "44443444", "44444444", "43444444", "44444434", "44444444", "44434444", ".444444."),
    ]),
]

PICKUPS = [  # score_orb, speed_boost, shield, key
    ("score_orb", S("........", "...a....", "..aaa...", ".aaeaa..", "..aaa...", "...a....", "........", "........")),
    ("speed_boost", S("........", ".5..5...", "..5..5..", "...5..5.", "..5..5..", ".5..5...", "........", "........")),
    ("shield", S("...bb...", "..b..b..", ".b....b.", ".b....b.", "..b..b..", "...bb...", "........", "........")),
    ("key", S("........", "........", ".55.....", "5..55555", "5..5.5.5", ".55.....", "........", "........")),
]

TILES = [  # floor, floor_alt, wall, wall_top, exit, void
    ("floor", S("32222222", "22222222", "22222222", "22222222", "22222222", "22222222", "22222222", "22222222")),
    ("floor_alt", S("22222222", "22222222", "22222222", "22222222", "22222222", "22222222", "22222222", "22222222")),
    ("wall", S("44444444", "33333333", "33333333", "33333333", "33333333", "33333333", "33333333", "33333333")),
    ("wall_top", S("44444444", "44444444", "44444444", "44444444", "44444444", "44444444", "44444444", "44444444")),
    ("exit", S("55555555", "51111115", "51111115", "51111115", "51111115", "51111115", "51111115", "55555555")),
    ("void", S("........", "........", "........", "........", "........", "........", "........", "........")),
]


def theme_palette_bytes(theme):
    pal = [0, 0, 0] + [v for h in THEMES[theme] for v in rgb(h)[:3]]
    return pal + [0] * (768 - len(pal))


def sprite_sheet(theme, entries, frames):
    """Indexed 8-px-tall sheet: entries x frames sprites left to right."""
    w = 8 * len(entries) * frames
    im = Image.new("P", (w, 8), 0)
    im.putpalette(theme_palette_bytes(theme))
    px = im.load()
    x0 = 0
    for name, art in entries:
        frs = art if frames > 1 else [art]
        assert len(frs) == frames, name
        for fr in frs:
            for y, row in enumerate(fr):
                for x, ch in enumerate(row):
                    px[x0 + x, y] = 0 if ch == "." else int(ch, 16)
            x0 += 8
    im.info["transparency"] = 0
    return im


# ---------------------------------------------------------------------------------------------------
# Manifest: relative path under textures/ -> painter (returns Img or PIL Image).  mcmeta frames noted.
# ---------------------------------------------------------------------------------------------------
MANIFEST = {}
MCMETA = {}  # path -> frametime


def reg(path, fn, frames=None, frametime=None):
    MANIFEST[path] = fn
    if frames:
        MCMETA[path] = {"frames": frames, "frametime": frametime}


reg("block/body.png", body_plain)
reg("block/body_light.png", body_light)
reg("block/body_dark.png", body_dark)
reg("block/trim.png", trim)
reg("block/ink.png", ink)
reg("block/cabinet_deck.png", cabinet_deck)
reg("block/cabinet_pad.png", cabinet_pad)
reg("block/cabinet_button_a_glow.png", lambda: button_glow(PALETTE["NEON"]))
reg("block/cabinet_button_b_glow.png", lambda: button_glow(PALETTE["CYAN"]))
reg("block/cabinet_slot.png", cabinet_slot)
reg("block/cabinet_cartridge_stub.png", cabinet_cartridge_stub)
reg("block/cabinet_vent.png", cabinet_vent)
reg("block/cabinet_bus_port_glow.png", bus_port_glow)
for _s, _h in (("off", "STATUS_OFF"), ("ready", "CYAN"), ("playing", "NEON"), ("linked", "AMBER"),
               ("locked", "DENY_RED"), ("no_power", "NO_POWER")):
    reg("block/status_%s_glow.png" % _s, (lambda h=_h: status_glow(PALETTE[h])))
reg("block/marquee_frame.png", marquee_frame)
reg("block/marquee_glow.png", marquee_glow_strip, frames=8, frametime=3)
reg("block/neon_glow.png", neon_glow)
reg("block/marquee_idle.png", marquee_idle)
reg("block/bench_top.png", bench_top)
reg("block/bench_holo_glow.png", bench_holo_glow)
reg("block/bench_tray.png", bench_tray)
reg("block/bench_console.png", bench_console)
reg("block/bus_hub.png", bus_hub)
reg("block/bus_hub_glow.png", bus_hub_glow)
reg("block/cable_sheath.png", cable_sheath)
reg("block/cable_glow.png", cable_glow_strip, frames=4, frametime=3)
reg("block/duplicator_deck.png", duplicator_deck)
reg("block/duplicator_top.png", duplicator_top)
reg("block/duplicator_platter.png", duplicator_platter)
reg("block/duplicator_hood.png", duplicator_hood)
reg("block/exchange_pedestal.png", exchange_pedestal)
reg("block/exchange_keypad.png", exchange_keypad)
reg("block/exchange_header_glow.png", exchange_header_glow)
reg("block/exchange_screen.png", exchange_screen)

reg("item/chip_base.png", chip_base)
reg("item/chip_band.png", chip_band)
reg("item/chip_band_stack.png", chip_band_stack)
for _slot in SLOT_GLYPHS:
    reg("item/chip_glyph_%s.png" % _slot, (lambda s=_slot: chip_glyph(s)))
reg("item/cartridge_base.png", cartridge_base)
reg("item/cartridge_band.png", cartridge_band)
reg("item/cartridge_label.png", cartridge_label)
reg("item/cartridge_rev.png", cartridge_rev)

reg("screen/font/arcade5x7.png", font_sheet)
for _t in THEMES:
    reg("screen/%s/pawns.png" % _t, (lambda t=_t: sprite_sheet(t, PAWNS, 2)))
    reg("screen/%s/hazards.png" % _t, (lambda t=_t: sprite_sheet(t, HAZARDS, 2)))
    reg("screen/%s/pickups.png" % _t, (lambda t=_t: sprite_sheet(t, PICKUPS, 1)))
    reg("screen/%s/tiles.png" % _t, (lambda t=_t: sprite_sheet(t, TILES, 1)))


def render(path):
    out = MANIFEST[path]()
    return out.im if isinstance(out, Img) else out


def save_png(im, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    if im.mode == "P":
        im.save(dest, "PNG", transparency=0, optimize=False)
    else:
        im.save(dest, "PNG", optimize=False)


def mcmeta_json(path):
    return {"animation": {"frametime": MCMETA[path]["frametime"]}}


def generate(only=None):
    n = 0
    for path in MANIFEST:
        if only and os.path.basename(path)[:-4] not in only:
            continue
        dest = os.path.join(TEX, path)
        save_png(render(path), dest)
        if path in MCMETA:
            with open(dest + ".mcmeta", "w", newline="\n") as f:
                json.dump(mcmeta_json(path), f, indent=2)
                f.write("\n")
        n += 1
    print("gen_arcade_textures: wrote %d texture(s)" % n)
    return 0


def same_pixels(a, b):
    if a.size != b.size:
        return False
    return a.convert("RGBA").tobytes() == b.convert("RGBA").tobytes()


def check():
    problems = []
    owned = set()
    for path in MANIFEST:
        dest = os.path.join(TEX, path)
        owned.add(os.path.normpath(dest))
        if not os.path.exists(dest):
            problems.append("missing: " + path)
            continue
        want = render(path)
        try:
            have = Image.open(dest)
            have.load()
        except Exception as e:  # pragma: no cover
            problems.append("unreadable: %s (%s)" % (path, e))
            continue
        if have.size != want.size:
            problems.append("size %s != %s: %s" % (have.size, want.size, path))
        elif not same_pixels(have, want):
            problems.append("pixels differ: " + path)
        if path.startswith("screen/") and not path.startswith("screen/font/") and have.mode != "P":
            problems.append("not indexed: " + path)
        if path in MCMETA:
            meta = dest + ".mcmeta"
            owned.add(os.path.normpath(meta))
            if not os.path.exists(meta):
                problems.append("missing mcmeta: " + path)
            else:
                with open(meta) as f:
                    if json.load(f) != mcmeta_json(path):
                        problems.append("mcmeta differs: " + path)
            fr = MCMETA[path]["frames"]
            if want.size[1] != want.size[0] * fr:
                problems.append("frame strip height wrong: " + path)
    for root, _dirs, files in os.walk(TEX):
        for fn in files:
            p = os.path.normpath(os.path.join(root, fn))
            if p not in owned and (fn.endswith(".png") or fn.endswith(".mcmeta")):
                problems.append("not owned by generator: " + os.path.relpath(p, TEX))
    for p in problems:
        print("  " + p)
    print("gen_arcade_textures --check: %d texture(s), %d problem(s)" % (len(MANIFEST), len(problems)))
    return 1 if problems else 0


def main(argv):
    if "--check" in argv:
        return check()
    only = None
    if "--only" in argv:
        only = set(argv[argv.index("--only") + 1].split(","))
    return generate(only)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
