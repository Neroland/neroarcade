#!/usr/bin/env python3
"""Generate one recipe-book unlock advancement per NeroArcade recipe.

For every ``data/neroarcade/recipe/<name>.json`` this writes
``data/neroarcade/advancement/recipes/<category>/<name>.json``: the recipe is unlocked the first time the player
holds one of its plain-item ingredients (a NeroArcade ingredient if the recipe has one, so every module chip unlocks
with the Blank Chip). Tag ingredients are never used as triggers (a tag another mod owns may be absent).

The advancements carry only the ``inventory_changed`` criterion: the ``recipe_unlocked`` condition's field name
differs between the Minecraft lines this mod builds for, and an advancement that is already granted never checks
again, so the second criterion adds nothing.

Usage (from anywhere):
    python tools/gen_recipe_unlocks.py           # write the advancements
    python tools/gen_recipe_unlocks.py --check   # exit 1 if a file is missing, stale or orphaned (writes nothing)
"""

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATA = REPO / "common" / "src" / "main" / "resources" / "data" / "neroarcade"
RECIPES = DATA / "recipe"
ADVANCEMENTS = DATA / "advancement" / "recipes"
NS = "neroarcade"


def ingredients(recipe):
    """Every ingredient string of a crafting recipe, in file order."""
    out = []
    if "key" in recipe:
        for value in recipe["key"].values():
            out.extend(value if isinstance(value, list) else [value])
    for value in recipe.get("ingredients", []):
        out.extend(value if isinstance(value, list) else [value])
    return [i for i in out if isinstance(i, str)]


def triggers(recipe):
    items = [i for i in ingredients(recipe) if not i.startswith("#")]
    own = [i for i in items if i.startswith(NS + ":")]
    chosen = own or items
    seen = []
    for item in chosen:
        if item not in seen:
            seen.append(item)
    return seen


def advancement(name, recipe):
    items = triggers(recipe)
    if not items:
        raise SystemExit(f"{name}: no plain-item ingredient to unlock it with")
    return {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_ingredient": {
                "conditions": {"items": [{"items": items[0] if len(items) == 1 else items}]},
                "trigger": "minecraft:inventory_changed",
            }
        },
        "requirements": [["has_ingredient"]],
        "rewards": {"recipes": [f"{NS}:{name}"]},
    }


def expected():
    out = {}
    for path in sorted(RECIPES.glob("*.json")):
        recipe = json.loads(path.read_text(encoding="utf-8"))
        category = recipe.get("category", "misc")
        target = ADVANCEMENTS / category / path.name
        out[target] = json.dumps(advancement(path.stem, recipe), indent=2) + "\n"
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    files = expected()
    existing = set(ADVANCEMENTS.rglob("*.json")) if ADVANCEMENTS.exists() else set()
    problems = []
    for path, text in files.items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current != text:
            if args.check:
                problems.append(f"stale or missing: {path.relative_to(REPO)}")
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                with open(path, "w", encoding="utf-8", newline="\n") as out:
                    out.write(text)
    for path in sorted(existing - set(files)):
        problems.append(f"orphan (no recipe): {path.relative_to(REPO)}")
    for line in problems:
        print(line)
    if problems:
        return 1
    print(f"{len(files)} recipe unlocks {'ok' if args.check else 'written'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
