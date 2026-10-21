#!/usr/bin/env python3
"""Cross-check the NeroArcade resource tree: every JSON parses, every texture a model references exists,
every model a blockstate / item definition references exists, every sound file sounds.json names exists,
and nothing under assets/ carries a private reference. Exit 1 on any problem.

usage: python3 tools/check_arcade_assets.py
"""
import glob
import json
import os
import re
import sys

sys.dont_write_bytecode = True

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets", "neroarcade")
NS = "neroarcade"
PRIVATE = re.compile(r"n[e]roland-mc-ec[o]system|n[e]roevents|R[E]WORK-PROMPT|P[L]AN-0\.1\.0|B[U]ILD-PROMPT|A[U]DIT-0|S[M]OKE-TEST", re.I)

problems = []


def load(path):
    with open(path, encoding="utf-8") as f:
        try:
            return json.load(f)
        except ValueError as e:
            problems.append("bad json: %s (%s)" % (os.path.relpath(path, ASSETS), e))
            return None


def exists_tex(ref):
    ns, _, p = ref.partition(":")
    if ns != NS:
        return True  # vanilla / other namespace: not ours to check
    return os.path.exists(os.path.join(ASSETS, "textures", p + ".png"))


def exists_model(ref):
    ns, _, p = ref.partition(":")
    if ns != NS:
        return True
    return os.path.exists(os.path.join(ASSETS, "models", p + ".json"))


def walk_models(obj, src):
    if isinstance(obj, dict):
        if "model" in obj and isinstance(obj["model"], str):
            if not exists_model(obj["model"]):
                problems.append("%s -> missing model %s" % (src, obj["model"]))
        for v in obj.values():
            walk_models(v, src)
    elif isinstance(obj, list):
        for v in obj:
            walk_models(v, src)


jsons = glob.glob(os.path.join(ASSETS, "**", "*.json"), recursive=True) + \
    glob.glob(os.path.join(ASSETS, "**", "*.mcmeta"), recursive=True)
for path in jsons:
    rel = os.path.relpath(path, ASSETS)
    obj = load(path)
    if obj is None:
        continue
    if rel.startswith("models"):
        tex = obj.get("textures", {})
        for k, v in tex.items():
            if not exists_tex(v):
                problems.append("%s -> missing texture %s (#%s)" % (rel, v, k))
        for e in obj.get("elements", []):
            for face, spec in e.get("faces", {}).items():
                key = spec["texture"].lstrip("#")
                if key not in tex:
                    problems.append("%s -> element %r face %s uses undefined #%s" % (rel, e.get("name"), face, key))
            rot = e.get("rotation")
            if rot and rot["angle"] not in (-45, -22.5, 0, 22.5, 45):
                problems.append("%s -> element %r illegal rotation %s" % (rel, e.get("name"), rot["angle"]))
        if "parent" in obj and not exists_model(obj["parent"]):
            problems.append("%s -> missing parent %s" % (rel, obj["parent"]))
    if rel.startswith("blockstates") or rel.startswith("items"):
        walk_models(obj, rel)

sj = os.path.join(ASSETS, "sounds.json")
if os.path.exists(sj):
    obj = load(sj)
    for key, entry in (obj or {}).items():
        for s in entry.get("sounds", []):
            if s.get("type") == "event":
                continue
            p = s["name"].partition(":")[2] + ".ogg"
            if not os.path.exists(os.path.join(ASSETS, "sounds", p)):
                problems.append("sounds.json %s -> missing sounds/%s" % (key, p))

for path in glob.glob(os.path.join(ASSETS, "**", "*"), recursive=True):
    if os.path.isfile(path) and not path.endswith((".png", ".ogg")):
        with open(path, encoding="utf-8", errors="replace") as f:
            if PRIVATE.search(f.read()):
                problems.append("private reference in " + os.path.relpath(path, ASSETS))
    if PRIVATE.search(os.path.basename(path)):
        problems.append("private reference in file name " + os.path.relpath(path, ASSETS))

for p in problems:
    print("  " + p)
print("check_arcade_assets: %d json file(s), %d problem(s)" % (len(jsons), len(problems)))
sys.exit(1 if problems else 0)
