#!/usr/bin/env python3
"""
Generate the NEROARCADE mod logo (square), in the shared Neroland family style: a deep-space
starfield with two nebula glows and the family faceted hexagonal core-prism with a glowing centre
node + specular sparkle.

NeroArcade is the mini-game construction-kit mod, so the family core-prism is the game running on
the screen of a neon hot-MAGENTA (#ff3fa4) arcade cabinet: marquee with pixel invaders, a CRT
screen with scanlines and pixel sprites, a joystick and three buttons, standing on a retro
perspective grid. The wordmark and tagline are drawn in a built-in 5x7 pixel font (no system font
needed, so the output is identical on every OS). Renders supersampled, then downsamples.

Outputs:
  art/logo/neroarcade_logo.png       (1024x1024 master)
  art/logo/neroarcade_logo_400.png   (CurseForge/Modrinth-ready)
  common/src/main/resources/neroarcade_logo.png  (256x256 in-game mods-list icon)

Usage: python tools/gen_logo.py   (needs Pillow and numpy)
"""
SEED = 47
MOD = 'neroarcade'
NAME = 'NEROARCADE'
TAG = 'BUILD · PLAY · SHARE'
ACCENT = (255, 63, 164)          # family colour #ff3fa4
ACCENT_BRIGHT = (255, 150, 206)
BRIGHT = (255, 232, 244)
NEBULA = (112, 18, 70)
STAR_TINT = (255, 200, 228)
TAG_COL = (244, 182, 214)
GOLD = (255, 214, 90)

import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "art", "logo")
ICON = os.path.join(ROOT, "common", "src", "main", "resources")

FINAL = 1024
SS = 2
R = FINAL * SS
rng = random.Random(SEED)

NERO_ALLOY = (38, 166, 154)     # family teal
STARSTEEL  = (140, 178, 208)    # family steel-blue
PLASMA     = (96, 212, 232)     # family cyan
FACET_COLS = [STARSTEEL, NERO_ALLOY, ACCENT, STARSTEEL, NERO_ALLOY, ACCENT]

# ---- 5x7 pixel font ------------------------------------------------------------------------------
GLYPHS = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'B': ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    'C': ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    'D': ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'H': ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'P': ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    'Y': ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    '·': ["00000", "00000", "00000", "00100", "00000", "00000", "00000"],
    ' ': ["00000"] * 7,
}

INVADER = ["00100000100", "00010001000", "00111111100", "01101110110",
           "11111111111", "10111111101", "10100000101", "00011011000"]
GHOST = ["0011100", "0111110", "1101011", "1111111", "1111111", "1010101"]


def text_cells(text, gap=1):
    """(col, row) of every lit pixel, and the text's width in pixel columns."""
    cells, x = [], 0
    for ch in text:
        g = GLYPHS[ch]
        for r, row in enumerate(g):
            for c, bit in enumerate(row):
                if bit == '1':
                    cells.append((x + c, r))
        x += len(g[0]) + gap
    return cells, x - gap


def sprite_cells(rows):
    return [(c, r) for r, row in enumerate(rows) for c, bit in enumerate(row) if bit == '1']


# ---- background ----------------------------------------------------------------------------------
def background():
    top = np.array([6, 11, 17], float)
    bot = np.array([13, 18, 28], float)
    yy = np.linspace(0, 1, R)[:, None, None]
    img = top[None, None, :] * (1 - yy) + bot[None, None, :] * yy
    img = np.repeat(img, R, axis=1)
    Y, X = np.mgrid[0:R, 0:R].astype(float)

    def glow(cx, cy, rad, color, strength):
        d = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        f = np.clip(1 - d / rad, 0, 1) ** 2 * strength
        for c in range(3):
            img[:, :, c] += color[c] * f

    glow(R * 0.22, R * 0.26, R * 0.55, (20, 80, 84), 0.42)   # family teal nebula
    glow(R * 0.80, R * 0.40, R * 0.55, NEBULA, 0.50)         # mod accent nebula
    glow(R * 0.5, R * 0.36, R * 0.40, (60, 20, 60), 0.30)    # glow behind the cabinet

    d = np.sqrt((X - R / 2) ** 2 + (Y - R / 2) ** 2) / (R * 0.72)
    vig = np.clip(1 - (d ** 2) * 0.85, 0.25, 1)
    img *= vig[:, :, None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def add_stars(base):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(380):
        x, y = rng.randint(0, R), rng.randint(0, int(R * 0.66))
        s = rng.choice([1, 1, 1, 2, 2, 3]) * SS
        b = rng.randint(120, 255)
        tint = rng.choice([(b, b, b), (b, 255, 255), STAR_TINT, (200, 200, 255)])
        d.rectangle([x, y, x + s, y + s], fill=tint + (rng.randint(120, 255),))  # square: pixel stars
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(2 * SS)))
    base.alpha_composite(layer)
    return base


def soft(draw_fn, blur):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    return layer.filter(ImageFilter.GaussianBlur(blur))


def neon(base, draw_fn, blur=10):
    """A shape drawn three times: wide glow, tight glow, crisp core."""
    base.alpha_composite(soft(draw_fn, blur * SS))
    base.alpha_composite(soft(draw_fn, max(1, blur * SS // 4)))
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    base.alpha_composite(layer)


def grid_floor(base, horizon):
    """A retro perspective grid from the horizon to the bottom edge, fading out with distance."""
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    vx = R / 2
    for i in range(-14, 15):
        x_bottom = vx + i * R * 0.11
        d.line([(vx + i * R * 0.012, horizon), (x_bottom, R)], fill=ACCENT + (120,), width=SS * 2)
    for k in range(1, 14):
        t = (k / 13) ** 2.1
        y = horizon + (R - horizon) * t
        d.line([(0, y), (R, y)], fill=ACCENT + (int(40 + 110 * t),), width=SS * 2)
    # fade toward the horizon
    mask = np.zeros((R, R), np.uint8)
    ys = np.arange(R)
    fade = np.clip((ys - horizon) / (R - horizon), 0, 1) ** 0.8
    mask[:] = (fade * 255).astype(np.uint8)[:, None]
    a = np.array(layer.split()[3]).astype(float) * (mask / 255.0)
    layer.putalpha(Image.fromarray(a.astype(np.uint8)))
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(4 * SS)))
    base.alpha_composite(layer)
    # horizon glow line
    neon(base, lambda dr: dr.line([(0, horizon), (R, horizon)], fill=ACCENT_BRIGHT + (200,), width=SS * 2), 12)


# ---- the family core-prism ----------------------------------------------------------------------
def draw_core_prism(base, cx, cy, rad):
    base.alpha_composite(soft(
        lambda dr: dr.ellipse([cx - rad * 1.3, cy - rad * 1.3, cx + rad * 1.3, cy + rad * 1.3],
                              fill=ACCENT + (110,)), 20 * SS))
    hexpts = [(cx + math.cos(math.radians(60 * i - 90)) * rad,
               cy + math.sin(math.radians(60 * i - 90)) * rad) for i in range(6)]
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(6):
        shade = 0.58 + 0.42 * (i / 5.0)
        col = tuple(int(c * shade) for c in FACET_COLS[i])
        d.polygon([(cx, cy), hexpts[i], hexpts[(i + 1) % 6]], fill=col + (255,))
    ir = rad * 0.36
    d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=ACCENT_BRIGHT + (255,))
    d.ellipse([cx - ir * 0.5, cy - ir * 0.5, cx + ir * 0.5, cy + ir * 0.5], fill=BRIGHT + (255,))
    for i in range(6):
        d.line([hexpts[i], hexpts[(i + 1) % 6]], fill=(230, 240, 250, 235), width=max(1, SS * 2))
        d.line([(cx, cy), hexpts[i]], fill=(220, 230, 240, 150), width=max(1, SS))
    base.alpha_composite(layer)
    sx, sy = cx - rad * 0.16, cy - rad * 0.46
    base.alpha_composite(soft(lambda dr: dr.ellipse([sx - 7 * SS, sy - 7 * SS, sx + 7 * SS, sy + 7 * SS],
                                                    fill=(255, 255, 255, 255)), 4 * SS))
    dd = ImageDraw.Draw(base)
    L = 13 * SS
    dd.line([sx - L, sy, sx + L, sy], fill=(255, 255, 255, 230), width=SS * 2)
    dd.line([sx, sy - L, sx, sy + L], fill=(255, 255, 255, 230), width=SS * 2)


# ---- the cabinet --------------------------------------------------------------------------------
def pixels(d, cells, ox, oy, px, fill, inset=0.12):
    g = px * inset
    for c, r in cells:
        x, y = ox + c * px, oy + r * px
        d.rectangle([x + g, y + g, x + px - g, y + px - g], fill=fill)


def cabinet(base):
    cx = R / 2
    # silhouette (front view): marquee, screen bezel, control deck, lower body
    top, bottom = R * 0.055, R * 0.635
    mw = R * 0.40          # marquee width
    bw = R * 0.36          # body width
    m_top, m_bot = top, top + R * 0.085
    s_top, s_bot = m_bot + R * 0.012, m_bot + R * 0.300
    deck_top, deck_bot = s_bot + R * 0.010, s_bot + R * 0.075
    dw = R * 0.43          # control deck juts out

    body = [(cx - bw / 2, m_bot), (cx + bw / 2, m_bot), (cx + bw / 2, deck_top),
            (cx + dw / 2, deck_top), (cx + dw / 2, deck_bot), (cx + bw / 2, deck_bot + R * 0.02),
            (cx + bw / 2, bottom), (cx - bw / 2, bottom), (cx - bw / 2, deck_bot + R * 0.02),
            (cx - dw / 2, deck_bot), (cx - dw / 2, deck_top), (cx - bw / 2, deck_top)]

    # cabinet glow + body fill
    base.alpha_composite(soft(lambda dr: dr.polygon(body, fill=ACCENT + (150,)), 40 * SS))
    shell = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shell)
    sd.polygon(body, fill=(22, 18, 34, 255))
    # side panels: darker vertical bands with a magenta pinstripe
    for sgn in (-1, 1):
        x0 = cx + sgn * bw / 2
        x1 = cx + sgn * (bw / 2 - R * 0.035)
        sd.rectangle([min(x0, x1), m_bot, max(x0, x1), bottom], fill=(34, 24, 52, 255))
    sd.rectangle([cx - dw / 2, deck_top, cx + dw / 2, deck_bot], fill=(40, 30, 60, 255))
    # kick plate + coin door
    sd.rectangle([cx - bw / 2 + R * 0.035, bottom - R * 0.05, cx + bw / 2 - R * 0.035, bottom],
                 fill=(14, 12, 22, 255))
    cdw, cdh = R * 0.07, R * 0.075
    cdy = deck_bot + R * 0.045
    sd.rounded_rectangle([cx - cdw, cdy, cx + cdw, cdy + cdh], radius=SS * 6, fill=(30, 26, 44, 255),
                         outline=STARSTEEL + (255,), width=SS * 3)
    base.alpha_composite(shell)

    # coin slots (glowing)
    for sgn in (-1, 1):
        sx = cx + sgn * cdw * 0.45
        neon(base, lambda dr, sx=sx: dr.rectangle([sx - SS * 4, cdy + cdh * 0.25, sx + SS * 4, cdy + cdh * 0.62],
                                                  fill=GOLD + (255,)), 6)

    # marquee: lit panel with pixel invaders
    mq = [cx - mw / 2, m_top, cx + mw / 2, m_bot]
    neon(base, lambda dr: dr.rounded_rectangle(mq, radius=SS * 10, fill=(70, 14, 52, 255),
                                               outline=ACCENT + (255,), width=SS * 5), 14)
    mql = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    md = ImageDraw.Draw(mql)
    inv = sprite_cells(INVADER)
    px = (m_bot - m_top) * 0.62 / len(INVADER)
    span = len(INVADER[0]) * px
    gap = (mw - 3 * span) / 4
    for i, col in enumerate((PLASMA, BRIGHT, PLASMA)):
        ox = cx - mw / 2 + gap + i * (span + gap)
        oy = m_top + ((m_bot - m_top) - len(INVADER) * px) / 2
        pixels(md, inv, ox, oy, px, col + (255,), inset=0.08)
    base.alpha_composite(mql.filter(ImageFilter.GaussianBlur(5 * SS)))
    base.alpha_composite(mql)

    # screen bezel + CRT
    sw = bw - R * 0.075
    bez = [cx - sw / 2 - R * 0.012, s_top, cx + sw / 2 + R * 0.012, s_bot]
    ImageDraw.Draw(base).rounded_rectangle(bez, radius=SS * 10, fill=(8, 8, 14, 255))
    scr = [cx - sw / 2, s_top + R * 0.014, cx + sw / 2, s_bot - R * 0.014]
    crt = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    cd = ImageDraw.Draw(crt)
    cd.rounded_rectangle(scr, radius=SS * 16, fill=(10, 22, 34, 255))
    base.alpha_composite(crt)
    # screen glow pool
    scx, scy = cx, (scr[1] + scr[3]) / 2
    base.alpha_composite(soft(lambda dr: dr.ellipse([scx - sw * 0.42, scy - sw * 0.36, scx + sw * 0.42,
                                                     scy + sw * 0.36], fill=(40, 120, 150, 120)), 30 * SS))
    # pixel sprites on screen: a ghost, a trail of pellets and a little pixel score
    spr = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    pd = ImageDraw.Draw(spr)
    gpx = R * 0.0072
    pixels(pd, sprite_cells(GHOST), scr[0] + sw * 0.08, scr[3] - R * 0.075, gpx, PLASMA + (255,), 0.06)
    for i in range(5):
        x = scr[0] + sw * 0.30 + i * sw * 0.11
        y = scr[3] - R * 0.052
        pd.rectangle([x, y, x + gpx * 1.6, y + gpx * 1.6], fill=GOLD + (255,))
    base.alpha_composite(spr.filter(ImageFilter.GaussianBlur(3 * SS)))
    base.alpha_composite(spr)
    draw_core_prism(base, scx, scy - R * 0.012, int(sw * 0.20))
    # scanlines + glass highlight over the CRT
    sl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    sld = ImageDraw.Draw(sl)
    y = scr[1]
    while y < scr[3]:
        sld.line([(scr[0], y), (scr[2], y)], fill=(0, 0, 0, 70), width=SS)
        y += SS * 4
    mask = Image.new("L", (R, R), 0)
    ImageDraw.Draw(mask).rounded_rectangle(scr, radius=SS * 16, fill=255)
    sl.putalpha(Image.fromarray(np.minimum(np.array(sl.split()[3]), np.array(mask)).astype(np.uint8)))
    base.alpha_composite(sl)
    hl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    ImageDraw.Draw(hl).polygon([(scr[0] + sw * 0.04, scr[1] + R * 0.01), (scr[0] + sw * 0.36, scr[1] + R * 0.01),
                                (scr[0] + sw * 0.14, scr[3] - R * 0.02), (scr[0] + sw * 0.02, scr[3] - R * 0.02)],
                               fill=(255, 255, 255, 18))
    base.alpha_composite(hl)
    neon(base, lambda dr: dr.rounded_rectangle(scr, radius=SS * 16, outline=PLASMA + (230,), width=SS * 3), 10)

    # control deck: joystick + three buttons
    dy = (deck_top + deck_bot) / 2
    jx = cx - dw * 0.26
    d = ImageDraw.Draw(base)
    d.ellipse([jx - R * 0.03, dy - R * 0.012, jx + R * 0.03, dy + R * 0.016], fill=(14, 12, 22, 255))
    d.line([(jx, dy), (jx + R * 0.006, dy - R * 0.026)], fill=(190, 196, 206, 255), width=SS * 7)
    bx, by, br = jx + R * 0.006, dy - R * 0.032, R * 0.017
    neon(base, lambda dr: dr.ellipse([bx - br, by - br, bx + br, by + br], fill=ACCENT + (255,)), 8)
    d.ellipse([bx - br * 0.45, by - br * 0.6, bx + br * 0.05, by - br * 0.1], fill=(255, 210, 235, 255))
    for i, col in enumerate((ACCENT, PLASMA, GOLD)):
        ux = cx + dw * 0.02 + i * dw * 0.12
        uy = dy - R * 0.004 - (R * 0.008 if i == 1 else 0)
        ur = R * 0.017
        neon(base, lambda dr, ux=ux, uy=uy, ur=ur, col=col:
             dr.ellipse([ux - ur, uy - ur * 0.8, ux + ur, uy + ur * 0.8], fill=col + (255,)), 8)
        d.ellipse([ux - ur * 0.55, uy - ur * 0.6, ux - ur * 0.05, uy - ur * 0.2], fill=(255, 255, 255, 170))

    # neon outline of the whole cabinet
    neon(base, lambda dr: dr.polygon(body, outline=ACCENT + (255,), width=SS * 4), 12)
    # floor reflection/shadow under the cabinet
    base.alpha_composite(soft(lambda dr: dr.ellipse([cx - bw * 0.7, bottom - R * 0.012, cx + bw * 0.7,
                                                     bottom + R * 0.03], fill=ACCENT + (150,)), 14 * SS))
    return bottom


# ---- pixel wordmark -----------------------------------------------------------------------------
def pixel_text(base, text, cy, max_w, px_max, face_top, face_bot, glow, extrude=0.0, gap=1):
    cells, cols = text_cells(text, gap)
    px = min(px_max, max_w / cols)
    w = cols * px
    ox, oy = (R - w) / 2, cy - 7 * px / 2
    # glow
    gl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    pixels(ImageDraw.Draw(gl), cells, ox, oy, px, glow + (255,), inset=0.0)
    base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(px * 1.1)))
    # extrusion (classic arcade 3-D drop)
    if extrude > 0:
        ex = Image.new("RGBA", (R, R), (0, 0, 0, 0))
        ed = ImageDraw.Draw(ex)
        steps = int(px * extrude)
        for k in range(steps, 0, -SS):
            shade = 0.25 + 0.35 * (1 - k / steps)
            pixels(ed, cells, ox + k * 0.55, oy + k, px, tuple(int(c * shade) for c in ACCENT) + (255,), 0.0)
        base.alpha_composite(ex)
    # face: vertical gradient, each pixel a bevelled block
    face = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    fd = ImageDraw.Draw(face)
    for c, r in cells:
        t = r / 6.0
        col = tuple(int(face_top[i] * (1 - t) + face_bot[i] * t) for i in range(3))
        x, y = ox + c * px, oy + r * px
        g = px * 0.06
        fd.rectangle([x + g, y + g, x + px - g, y + px - g], fill=col + (255,))
        hi = tuple(min(255, int(v + (255 - v) * 0.55)) for v in col)
        fd.rectangle([x + g, y + g, x + px - g, y + g + px * 0.18], fill=hi + (255,))
        lo = tuple(int(v * 0.72) for v in col)
        fd.rectangle([x + g, y + px - g - px * 0.14, x + px - g, y + px - g], fill=lo + (255,))
    base.alpha_composite(face)
    return oy + 7 * px


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(ICON, exist_ok=True)
    img = background()
    img = add_stars(img)
    horizon = R * 0.60
    grid_floor(img, horizon)
    cabinet(img)
    # dark band behind the wordmark so it reads over the grid
    img.alpha_composite(soft(lambda dr: dr.rectangle([0, R * 0.665, R, R * 0.935], fill=(6, 8, 14, 205)), 30 * SS))
    pixel_text(img, NAME, R * 0.755, R * 0.90, R * 0.016, BRIGHT, ACCENT, ACCENT, extrude=0.55)
    pixel_text(img, TAG, R * 0.885, R * 0.62, R * 0.0072, TAG_COL, TAG_COL, ACCENT)

    final = img.convert("RGB").resize((FINAL, FINAL), Image.LANCZOS)
    p1 = os.path.join(OUT, MOD + "_logo.png")
    p2 = os.path.join(OUT, MOD + "_logo_400.png")
    p3 = os.path.join(ICON, MOD + "_logo.png")
    final.save(p1)
    final.resize((400, 400), Image.LANCZOS).save(p2)
    final.resize((256, 256), Image.LANCZOS).save(p3)
    for p in (p1, p2, p3):
        print("wrote", os.path.relpath(p, ROOT))


if __name__ == "__main__":
    main()
