#!/usr/bin/env python3
"""Emit NeroArcade's block models, blockstates, item models and client item definitions.

The geometry is authored here as element tables (16-unit block space, front = north = z 0) and written
out as plain vanilla JSON so the resource pack carries no generator dependency. Re-run after editing;
the output is deterministic. ``--check`` regenerates in memory and fails if any file on disk differs.

Rotation grid: vanilla element rotations are limited to +-22.5 / +-45, so the cabinet's control deck
and the screen plates tilt 22.5 degrees (the visual brief said 15 / 12; recorded as a deviation).

usage: python3 tools/gen_arcade_models.py [--check]
"""
import json
import os
import sys

sys.dont_write_bytecode = True

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets", "neroarcade")
NS = "neroarcade"

FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
SLOTS = ("arena", "pawn", "rule", "hazard", "pickup", "control", "pace", "modifier", "multiplayer", "theme")
SLOT_COLOURS = {
    "arena": 0x2fe3ff, "pawn": 0xff3fa4, "rule": 0xffb347, "hazard": 0xff5c5c, "pickup": 0x58e07a,
    "control": 0xe8ecf4, "pace": 0xb48cff, "modifier": 0x8f62ff, "multiplayer": 0x22b8d8, "theme": 0xff8ccf,
}
GREY = 0x8a93a6
WHITE = 0xffffff
STATUSES = ("off", "ready", "playing", "linked", "locked", "no_power")

FILES = {}  # relative path under assets/neroarcade -> python object


def out(path, obj):
    FILES[path] = obj


def tex(name):
    return "%s:block/%s" % (NS, name)


# ---------------------------------------------------------------------------------------------------
# Element helpers
# ---------------------------------------------------------------------------------------------------
def el(name, frm, to, texture=None, faces=None, shade=None, rot=None, uv=None, cull=None):
    """One model element.

    texture: default texture key (used for every face not listed in faces).
    faces:   {"north": "key" | {"texture": "key", "uv": [...]}, ...}; when given, ONLY these faces exist.
    uv:      explicit uv applied to every generated face (else vanilla auto-UV from the element bounds).
    rot:     (origin, axis, angle).
    """
    e = {"name": name, "from": list(frm), "to": list(to)}
    if rot:
        e["rotation"] = {"origin": list(rot[0]), "axis": rot[1], "angle": rot[2]}
    if shade is not None:
        e["shade"] = shade
    f = {}
    if faces is None:
        for side in ("north", "south", "east", "west", "up", "down"):
            d = {"texture": "#" + texture}
            if uv:
                d["uv"] = list(uv)
            if cull and side in cull:
                d["cullface"] = side
            f[side] = d
    else:
        for side, spec in faces.items():
            if isinstance(spec, str):
                d = {"texture": "#" + spec}
                if uv:
                    d["uv"] = list(uv)
            else:
                d = dict(spec)
                d["texture"] = "#" + d["texture"]
            f[side] = d
    e["faces"] = f
    return e


def model(textures, elements, particle=None, parent="minecraft:block/block"):
    t = {k: tex(v) for k, v in textures.items()}
    t["particle"] = tex(particle or list(textures.values())[0])
    return {"parent": parent, "textures": t, "elements": elements}


def scaled(elements, s, dy=0.0):
    """Uniformly scale elements about the block's bottom-centre (8, 0, 8) and lift by dy."""
    def p(v):
        return [round((v[0] - 8) * s + 8, 3), round(v[1] * s + dy, 3), round((v[2] - 8) * s + 8, 3)]
    res = []
    for e in elements:
        n = json.loads(json.dumps(e))
        n["from"], n["to"] = p(e["from"]), p(e["to"])
        if "rotation" in n:
            n["rotation"]["origin"] = p(e["rotation"]["origin"])
        res.append(n)
    return res


# ---------------------------------------------------------------------------------------------------
# Arcade Cabinet (1x2). Lower half carries the control deck; upper half the screen and marquee.
# ---------------------------------------------------------------------------------------------------
DECK_ROT = ([8, 9, 6], "x", -22.5)   # front edge dips toward the player

CAB_LOWER_TEX = {"body": "body", "dark": "body_dark", "light": "body_light", "slot": "cabinet_slot",
                 "deck": "cabinet_deck"}
cab_lower = [
    el("kick", [1, 0, 1], [15, 2, 15], "dark"),
    el("body", [1, 2, 2], [15, 16, 16],
       faces={"north": "body", "south": {"texture": "body", "cullface": "south"}, "east": "slot",
              "west": "body", "up": {"texture": "body", "cullface": "up"}, "down": "dark"}),
    el("apron", [1, 2, 0], [15, 9, 2],
       faces={"north": "body", "east": "body", "west": "body", "down": "dark"}),
    el("deck", [1, 9, 0], [15, 12, 6], "deck", rot=DECK_ROT),
]
out("models/block/arcade_cabinet_lower.json", model(CAB_LOWER_TEX, cab_lower, "body"))


def pad_model(pressed):
    arms = {
        "n": ([4.5, 12, 1], [5.5, 13, 2.5]),
        "s": ([4.5, 12, 3.5], [5.5, 13, 5]),
        "w": ([3, 12, 2.5], [4.5, 13, 3.5]),
        "e": ([5.5, 12, 2.5], [7, 13, 3.5]),
        "c": ([4.5, 12, 2.5], [5.5, 13, 3.5]),
    }
    els = []
    for k, (a, b) in arms.items():
        a, b = list(a), list(b)
        if k == pressed:
            a[1] -= 1
            b[1] -= 1
        els.append(el("pad_" + k, a, b, "pad", rot=DECK_ROT))
    return model({"pad": "cabinet_pad"}, els)


out("models/block/arcade_cabinet_pad_none.json", pad_model(None))
for d in "nsew":
    out("models/block/arcade_cabinet_pad_%s.json" % d, pad_model(d))


def button_model(which, pressed):
    x0 = 9 if which == "a" else 12
    y0 = 11 if pressed else 12
    return model({"glow": "cabinet_button_%s_glow" % which},
                 [el("button_" + which, [x0, y0, 2], [x0 + 2, y0 + 2, 4], "glow", shade=False, rot=DECK_ROT)])


for w in "ab":
    out("models/block/arcade_cabinet_button_%s.json" % w, button_model(w, False))
    out("models/block/arcade_cabinet_button_%s_pressed.json" % w, button_model(w, True))

for s in STATUSES:
    out("models/block/arcade_cabinet_status_%s.json" % s, model(
        {"glow": "status_%s_glow" % s},
        [el("status", [3, 14, 1.5], [13, 15, 2], faces={"north": {"texture": "glow", "uv": [3, 1, 13, 2]}},
            shade=False)]))

out("models/block/arcade_cabinet_cartridge.json", model(
    {"stub": "cabinet_cartridge_stub"}, [el("stub", [15, 5, 9], [17, 7, 13], "stub")]))

out("models/block/arcade_cabinet_bus_port.json", model(
    {"glow": "cabinet_bus_port_glow"},
    [el("port", [6, 3, 16], [10, 5, 16.5], faces={"south": {"texture": "glow", "uv": [0, 0, 16, 16]}},
        shade=False)]))

SCREEN_ROT = ([8, 2, 1], "x", 22.5)  # top leans back into the body
CAB_UPPER_TEX = {"body": "body", "dark": "body_dark", "ink": "ink", "vent": "cabinet_vent",
                 "marquee": "marquee_frame", "mglow": "marquee_glow", "neon": "neon_glow"}
cab_upper = [
    el("body", [1, 0, 6], [15, 12, 16],
       faces={"south": {"texture": "vent", "cullface": "south"}, "east": "body", "west": "body",
              "up": "body", "down": "dark"}),
    el("pillar_w", [1, 0, 0], [3, 12, 6], "body"),
    el("pillar_e", [13, 0, 0], [15, 12, 6], "body"),
    el("sill", [3, 0, 0], [13, 2, 6], "body"),
    el("screen", [3, 2, 1], [13, 12, 2],
       faces={"north": {"texture": "ink", "uv": [3, 2, 13, 12]}, "up": "dark", "east": "dark", "west": "dark"},
       rot=SCREEN_ROT),
    el("marquee", [1, 12, 0], [15, 16, 16],
       faces={"north": {"texture": "marquee", "uv": [1, 12, 15, 16]}, "south": {"texture": "body", "cullface": "south"},
              "east": "body", "west": "body", "up": {"texture": "body", "cullface": "up"}, "down": "dark"}),
    el("marquee_glow", [2, 13, -0.5], [14, 15, 0],
       faces={"north": {"texture": "mglow", "uv": [2, 13, 14, 15]}}, shade=False),
    el("neon_w", [0, 0, 0], [1, 12, 1], "neon", shade=False),
    el("neon_e", [15, 0, 0], [16, 12, 1], "neon", shade=False),
]
out("models/block/arcade_cabinet_upper.json", model(CAB_UPPER_TEX, cab_upper, "body"))

# Inventory model: both halves at half scale so the item reads as a 1x2 cabinet.
inv_tex = dict(CAB_LOWER_TEX)
inv_tex.update(CAB_UPPER_TEX)
inv_tex.update({"pad": "cabinet_pad", "glow_a": "cabinet_button_a_glow", "glow_b": "cabinet_button_b_glow",
                "status": "status_ready_glow"})
inv_lower = cab_lower + pad_model(None)["elements"] + [
    el("button_a", [9, 12, 2], [11, 14, 4], "glow_a", shade=False, rot=DECK_ROT),
    el("button_b", [12, 12, 2], [14, 14, 4], "glow_b", shade=False, rot=DECK_ROT),
    el("status", [3, 14, 1.5], [13, 15, 2], faces={"north": {"texture": "status", "uv": [3, 1, 13, 2]}}, shade=False),
]
out("models/block/arcade_cabinet_inventory.json", {
    "parent": "minecraft:block/block",
    "textures": {**{k: tex(v) for k, v in inv_tex.items()}, "particle": tex("body")},
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"scale": [0.5, 0.5, 0.5]},
    },
    "elements": scaled(inv_lower, 0.5) + scaled(cab_upper, 0.5, 8),
})


def multipart_facing(cond, model_name, **extra):
    """Four multipart entries (one per facing) for one condition dict."""
    entries = []
    for facing, y in FACINGS:
        when = dict(cond)
        when["facing"] = facing
        apply = {"model": "%s:block/%s" % (NS, model_name)}
        if y:
            apply["y"] = y
        apply.update(extra)
        entries.append({"when": when, "apply": apply})
    return entries


cab_parts = []
cab_parts += multipart_facing({"half": "upper"}, "arcade_cabinet_upper")
cab_parts += multipart_facing({"half": "lower"}, "arcade_cabinet_lower")
for d in ("none", "n", "s", "e", "w"):
    cab_parts += multipart_facing({"half": "lower", "pad": d}, "arcade_cabinet_pad_" + d)
for w in "ab":
    cab_parts += multipart_facing({"half": "lower", "btn_" + w: "false"}, "arcade_cabinet_button_" + w)
    cab_parts += multipart_facing({"half": "lower", "btn_" + w: "true"}, "arcade_cabinet_button_%s_pressed" % w)
for s in STATUSES:
    cab_parts += multipart_facing({"half": "lower", "status": s}, "arcade_cabinet_status_" + s)
cab_parts += multipart_facing({"half": "lower", "cartridge": "true"}, "arcade_cabinet_cartridge")
cab_parts += multipart_facing({"half": "lower", "linked": "true"}, "arcade_cabinet_bus_port")
out("blockstates/arcade_cabinet.json", {"multipart": cab_parts})

# ---------------------------------------------------------------------------------------------------
# Game Bench
# ---------------------------------------------------------------------------------------------------
bench = [
    el("leg_nw", [1, 0, 1], [3, 10, 3], "dark"),
    el("leg_ne", [13, 0, 1], [15, 10, 3], "dark"),
    el("leg_sw", [1, 0, 13], [3, 10, 15], "dark"),
    el("leg_se", [13, 0, 13], [15, 10, 15], "dark"),
    el("top", [0, 10, 0], [16, 12, 16],
       faces={"up": "top", "down": "dark", "north": "body", "south": "body", "east": "body", "west": "body"}),
    el("holo", [2, 12, 2], [14, 13, 14],
       faces={"up": {"texture": "holo", "uv": [2, 2, 14, 14]}, "north": "dark", "south": "dark", "east": "dark", "west": "dark"},
       shade=False),
    el("tray", [3, 5, 0], [13, 11, 4],
       faces={"north": {"texture": "tray", "uv": [3, 5, 13, 11]}, "east": "body", "west": "body", "down": "dark"}),
    el("console_w", [0, 12, 11], [4, 14, 16], "console"),
    el("console_e", [12, 12, 11], [16, 14, 16], "console"),
]
out("models/block/game_bench.json", model(
    {"body": "body", "dark": "body_dark", "top": "bench_top", "holo": "bench_holo_glow", "tray": "bench_tray",
     "console": "bench_console"}, bench, "bench_top"))
out("models/block/game_bench_cartridge.json", model(
    {"stub": "cabinet_cartridge_stub"}, [el("stub", [5, 7, -1], [11, 9, 1], "stub")]))
bench_parts = multipart_facing({}, "game_bench") + multipart_facing({"cartridge": "true"}, "game_bench_cartridge")
out("blockstates/game_bench.json", {"multipart": bench_parts})

# ---------------------------------------------------------------------------------------------------
# Link Bus hub + Link Cable
# ---------------------------------------------------------------------------------------------------
hub = [
    el("body", [2, 2, 2], [14, 14, 14], "hub"),
    el("port_n", [6, 6, 0], [10, 10, 2], "dark"),
    el("port_s", [6, 6, 14], [10, 10, 16], "dark"),
    el("port_w", [0, 6, 6], [2, 10, 10], "dark"),
    el("port_e", [14, 6, 6], [16, 10, 10], "dark"),
    el("glow", [2, 14, 2], [14, 14.5, 14], faces={"up": {"texture": "glow", "uv": [2, 2, 14, 14]}}, shade=False),
]
out("models/block/link_bus.json", model({"hub": "bus_hub", "dark": "body_dark", "glow": "bus_hub_glow"}, hub, "bus_hub"))
out("blockstates/link_bus.json", {"variants": {
    "facing=%s" % f: ({"model": tex("link_bus"), "y": y} if y else {"model": tex("link_bus")}) for f, y in FACINGS}})

cable_tex = {"sheath": "cable_sheath", "glow": "cable_glow"}
core = [el("core", [6, 6, 6], [10, 10, 10], "sheath")]
core_glow = [el("core_glow", [5.75, 5.75, 5.75], [10.25, 10.25, 10.25], "glow", shade=False)]
arm = [el("arm", [6, 6, 0], [10, 10, 6], "sheath")]
arm_glow = [el("arm_glow", [5.75, 5.75, 0], [10.25, 10.25, 6], "glow", shade=False)]
out("models/block/link_cable_core.json", model(cable_tex, core, "cable_sheath"))
out("models/block/link_cable_core_active.json", model(cable_tex, core + core_glow, "cable_sheath"))
out("models/block/link_cable_arm.json", model(cable_tex, arm, "cable_sheath"))
out("models/block/link_cable_arm_active.json", model(cable_tex, arm + arm_glow, "cable_sheath"))
ARM_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
cable_parts = [
    {"when": {"active": "false"}, "apply": {"model": tex("link_cable_core")}},
    {"when": {"active": "true"}, "apply": {"model": tex("link_cable_core_active")}},
]
for d, r in ARM_ROT.items():
    for active, name in (("false", "link_cable_arm"), ("true", "link_cable_arm_active")):
        cable_parts.append({"when": {d: "true", "active": active}, "apply": {"model": tex(name), **r}})
out("blockstates/link_cable.json", {"multipart": cable_parts})

# ---------------------------------------------------------------------------------------------------
# Cartridge Duplicator
# ---------------------------------------------------------------------------------------------------
dup_tex = {"deck": "duplicator_deck", "top": "duplicator_top", "dark": "body_dark", "light": "body_light",
           "platter": "duplicator_platter", "hood": "duplicator_hood"}
dup_base = [
    el("deck", [0, 0, 0], [16, 8, 16],
       faces={"up": "top", "down": {"texture": "dark", "cullface": "down"}, "north": "deck", "south": "deck",
              "east": "deck", "west": "deck"}),
    el("hood_post", [12, 8, 8], [14, 14, 10], "hood"),
    el("hood_arm", [6, 13, 8], [14, 14, 10], "hood"),
    el("hood_head", [7, 12, 8], [9, 13, 10], "dark"),
]
platter = [el("platter", [4, 8, 6], [12, 9, 14],
              faces={"up": {"texture": "platter", "uv": [0, 0, 16, 16]}, "north": "light", "south": "light",
                     "east": "light", "west": "light"})]
out("models/block/cartridge_duplicator.json", model(dup_tex, dup_base + platter, "duplicator_deck"))
out("models/block/cartridge_duplicator_copying.json", model(dup_tex, dup_base, "duplicator_deck"))
out("models/block/cartridge_duplicator_original.json", model(
    {"stub": "cabinet_cartridge_stub"}, [el("stub", [3, 8, 2], [6, 10, 4], "stub")]))
out("models/block/cartridge_duplicator_blank.json", model(
    {"stub": "cabinet_cartridge_stub"}, [el("stub", [10, 8, 2], [13, 10, 4], "stub")]))
dup_parts = []
dup_parts += multipart_facing({"copying": "false"}, "cartridge_duplicator")
dup_parts += multipart_facing({"copying": "true"}, "cartridge_duplicator_copying")
dup_parts += multipart_facing({"original": "true"}, "cartridge_duplicator_original")
dup_parts += multipart_facing({"blank": "true"}, "cartridge_duplicator_blank")
out("blockstates/cartridge_duplicator.json", {"multipart": dup_parts})

# ---------------------------------------------------------------------------------------------------
# Arcade Exchange (1x2 kiosk)
# ---------------------------------------------------------------------------------------------------
xl_tex = {"dark": "body_dark", "pedestal": "exchange_pedestal", "keypad": "exchange_keypad", "light": "body_light",
          "tray": "bench_tray", "body": "body"}
exchange_lower = [
    el("base", [0, 0, 0], [16, 2, 16], "dark", cull=("down",)),
    el("pedestal", [2, 2, 2], [14, 12, 14], "pedestal"),
    el("keypad", [3, 12, 2], [13, 14, 10],
       faces={"up": "keypad", "north": "light", "south": "light", "east": "light", "west": "light"}),
    el("tray", [3, 5, 0], [13, 11, 2],
       faces={"north": {"texture": "tray", "uv": [3, 5, 13, 11]}, "east": "body", "west": "body", "up": "body", "down": "dark"}),
]
out("models/block/arcade_exchange_lower.json", model(xl_tex, exchange_lower, "exchange_pedestal"))

XSCREEN_ROT = ([8, 2, 2], "x", 22.5)
xu_tex = {"body": "body", "dark": "body_dark", "light": "body_light", "screen": "exchange_screen",
          "header": "exchange_header_glow"}
exchange_upper = [
    el("body", [2, 0, 7], [14, 14, 14], "body"),
    el("cheek_w", [2, 0, 2], [3, 12, 7], "body"),
    el("cheek_e", [13, 0, 2], [14, 12, 7], "body"),
    el("screen", [3, 2, 2], [13, 12, 3],
       faces={"north": {"texture": "screen", "uv": [3, 2, 13, 12]}, "up": "dark", "east": "dark", "west": "dark", "down": "dark"},
       rot=XSCREEN_ROT),
    el("header_body", [2, 12, 2.5], [14, 14, 7], "body"),
    el("header_glow", [2, 12, 2], [14, 14, 2.5],
       faces={"north": {"texture": "header", "uv": [2, 12, 14, 14]}}, shade=False),
    el("cap", [1, 14, 2], [15, 16, 15], "light"),
]
out("models/block/arcade_exchange_upper.json", model(xu_tex, exchange_upper, "body"))
out("models/block/arcade_exchange_inventory.json", {
    "parent": "minecraft:block/block",
    "textures": {**{k: tex(v) for k, v in {**xl_tex, **xu_tex}.items()}, "particle": tex("exchange_pedestal")},
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"scale": [0.5, 0.5, 0.5]},
    },
    "elements": scaled(exchange_lower, 0.5) + scaled(exchange_upper, 0.5, 8),
})
xch_parts = multipart_facing({"half": "lower"}, "arcade_exchange_lower") + \
    multipart_facing({"half": "upper"}, "arcade_exchange_upper")
out("blockstates/arcade_exchange.json", {"multipart": xch_parts})

# ---------------------------------------------------------------------------------------------------
# Marquee Screen (1x1, BER billboard grows from the block; static = bracket + amber frame)
# ---------------------------------------------------------------------------------------------------
AMBER_UV = {"texture": "frame", "uv": [1, 12, 15, 13]}
marquee = [
    el("bracket", [6, 0, 12], [10, 4, 16], "dark", cull=("south", "down")),
    el("panel", [1, 2, 10], [15, 16, 12],
       faces={"north": {"texture": "ink", "uv": [1, 2, 15, 16]}, "south": "body", "east": "body", "west": "body",
              "up": "body", "down": "dark"}),
    el("edge_bottom", [1, 2, 9.5], [15, 3, 10], faces={"north": AMBER_UV, "up": AMBER_UV, "down": AMBER_UV}, shade=False),
    el("edge_top", [1, 15, 9.5], [15, 16, 10], faces={"north": AMBER_UV, "up": AMBER_UV, "down": AMBER_UV}, shade=False),
    el("edge_w", [1, 3, 9.5], [2, 15, 10], faces={"north": AMBER_UV, "west": AMBER_UV}, shade=False),
    el("edge_e", [14, 3, 9.5], [15, 15, 10], faces={"north": AMBER_UV, "east": AMBER_UV}, shade=False),
]
out("models/block/marquee_screen.json", model(
    {"dark": "body_dark", "body": "body", "ink": "ink", "frame": "marquee_frame"}, marquee, "body"))
out("blockstates/marquee_screen.json", {"variants": {
    "facing=%s" % f: ({"model": tex("marquee_screen"), "y": y} if y else {"model": tex("marquee_screen")})
    for f, y in FACINGS}})

# ---------------------------------------------------------------------------------------------------
# Item models + client item definitions (items/<id>.json)
# ---------------------------------------------------------------------------------------------------
def plain_item(item_id, model_path):
    out("items/%s.json" % item_id, {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, model_path)}})


def block_item(item_id, block_model):
    out("models/item/%s.json" % item_id, {"parent": "%s:block/%s" % (NS, block_model)})
    plain_item(item_id, item_id)


block_item("arcade_cabinet", "arcade_cabinet_inventory")
block_item("game_bench", "game_bench")
block_item("link_bus", "link_bus")
block_item("cartridge_duplicator", "cartridge_duplicator")
block_item("arcade_exchange", "arcade_exchange_inventory")
block_item("marquee_screen", "marquee_screen")

# Link cable as an item: core + one arm each side reads as a cable segment.
out("models/block/link_cable_inventory.json", model(
    cable_tex, core + [el("arm_n", [6, 6, 0], [10, 10, 6], "sheath"), el("arm_s", [6, 6, 10], [10, 10, 16], "sheath")],
    "cable_sheath"))
block_item("link_cable", "link_cable_inventory")


def generated(layers):
    return {"parent": "minecraft:item/generated",
            "textures": {"layer%d" % i: "%s:item/%s" % (NS, t) for i, t in enumerate(layers)}}


def const(v):
    return {"type": "minecraft:constant", "value": v}


def cmd_tint(default):
    return {"type": "minecraft:custom_model_data", "index": 0, "default": default}


# Module chip: one item id, one model per slot (the glyph texture differs); band + glyph (+ stack band)
# tinted from custom_model_data colour index 0, defaulting to the slot colour.
for slot in SLOTS:
    layers = ["chip_base", "chip_band", "chip_glyph_" + slot]
    if slot in ("hazard", "pickup"):
        layers.append("chip_band_stack")
    out("models/item/module_chip_%s.json" % slot, generated(layers))
out("models/item/blank_chip.json", generated(["chip_base", "chip_band"]))

cases = []
for slot in SLOTS:
    n_tints = 4 if slot in ("hazard", "pickup") else 3
    cases.append({"when": slot, "model": {
        "type": "minecraft:model", "model": "%s:item/module_chip_%s" % (NS, slot),
        "tints": [const(WHITE)] + [cmd_tint(SLOT_COLOURS[slot])] * (n_tints - 1)}})
out("items/module_chip.json", {"model": {
    "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
    "cases": cases,
    "fallback": {"type": "minecraft:model", "model": "%s:item/blank_chip" % NS, "tints": [const(WHITE), const(GREY)]}}})
out("items/blank_chip.json", {"model": {
    "type": "minecraft:model", "model": "%s:item/blank_chip" % NS, "tints": [const(WHITE), const(GREY)]}})

# Cartridge: band tinted to the THEME chip's signature colour (custom_model_data colour index 0).
out("models/item/cartridge.json", generated(["cartridge_base", "cartridge_band", "cartridge_label", "cartridge_rev"]))
out("models/item/blank_cartridge.json", generated(["cartridge_base", "cartridge_band"]))
out("items/cartridge.json", {"model": {
    "type": "minecraft:model", "model": "%s:item/cartridge" % NS,
    "tints": [const(WHITE), cmd_tint(GREY), const(WHITE), const(WHITE)]}})
out("items/blank_cartridge.json", {"model": {
    "type": "minecraft:model", "model": "%s:item/blank_cartridge" % NS, "tints": [const(WHITE), const(GREY)]}})


# ---------------------------------------------------------------------------------------------------
def dump(obj):
    return json.dumps(obj, indent=2) + "\n"


def main(argv):
    check = "--check" in argv
    problems = 0
    for rel, obj in FILES.items():
        dest = os.path.join(ASSETS, rel)
        text = dump(obj)
        if check:
            if not os.path.exists(dest):
                print("  missing: " + rel)
                problems += 1
            else:
                with open(dest, encoding="utf-8") as f:
                    if f.read().replace("\r\n", "\n") != text:
                        print("  differs: " + rel)
                        problems += 1
        else:
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
    if check:
        print("gen_arcade_models --check: %d file(s), %d problem(s)" % (len(FILES), problems))
        return 1 if problems else 0
    print("gen_arcade_models: wrote %d file(s)" % len(FILES))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
