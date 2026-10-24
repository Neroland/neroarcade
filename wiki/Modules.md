# Modules

A NeroArcade game is ten **slots**, always in the same order. Each slot holds one **module chip** that decides one
thing about the game; the Hazard and Pickup slots hold up to three chips each. A slot left empty uses its default.
There are 43 chips in this version.

| Slot | Decides | Holds | Empty means | Ingredient | Chips |
| --- | --- | --- | --- | --- | --- |
| [Arena](Modules-Arena.md) | The board: size, walls, whether it scrolls or wraps | 1 | Grid 16 | glass pane | 4 |
| [Pawn](Modules-Pawn.md) | What each player steers and how it moves | 1 | Cursor | Nero Alloy | 6 |
| [Rule](Modules-Rule.md) | How a match is won or lost | 1 | Survive (60 s, 1 life) | paper | 5 |
| [Hazard](Modules-Hazard.md) | What threatens the pawns | up to 3 | no hazard | gunpowder | 5 |
| [Pickup](Modules-Pickup.md) | What the pawns collect | up to 3 | no pickup | gold nugget | 4 |
| [Control](Modules-Control.md) | How the buttons become movement | 1 | Four-way | lever | 4 |
| [Pace](Modules-Pace.md) | How fast the game runs, and whether that changes | 1 | Steady (full speed) | repeater | 3 |
| [Modifier](Modules-Modifier.md) | One twist on the whole game | 1 | no modifier | amethyst shard | 4 |
| [Multiplayer](Modules-Multiplayer.md) | How many play, together or against each other | 1 | Solo | copper ingot | 4 |
| [Theme](Modules-Theme.md) | Colours, sprites and sounds of the screen | 1 | Neon Void | pink dye | 4 |

## Crafting chips

Every chip starts as a **Blank Chip** (copper ingot, redstone, copper ingot in a row; makes four). Craft a Blank
Chip with its slot's **ingredient** (the table above) and, for every chip except the seven defaults, **one more
item** that tells the chips of a slot apart. All chip recipes are shapeless. Each slot page lists the full recipe of
every chip. Chips are never used up by the bench.

## Settings

Most chips have settings, changed on the Game Bench by clicking the chip's row. A setting is always a whole number
in a fixed range, an on/off switch, or a choice from a short list — never free text. The slot pages list every
setting with its default and range. A cartridge from another server whose settings are out of range, or that uses a
chip this server does not have, shows "UNREADABLE HERE" with the reason and cannot be played here.

Speeds are on a 1–5 scale. Times in **ticks** are game ticks: 20 ticks are one second.

## Warnings

The bench shows a grey warning (▲) on a row when a combination is legal but odd. The game still plays. The
warnings in this version:

| Warning | When |
| --- | --- |
| "Nothing can hurt the pawn, so this is won by waiting." | A Survive rule with no hazard that can hurt. |
| "This rule needs points, but no pickup gives any. The match can only end by lives or time." | Score Target, and no pickup, hazard or pawn that gives points. |
| "There is nothing to clear, so this plays as a survival game." | Clear Board with no clearable pickup. |
| "Nothing uses the key, so it counts as an orb worth 5 points." | A Key pickup without a Reach Exit rule that needs it. |
| "The pawn moves on one line, so the exit is placed at the far end of that line." | Reach Exit with a pawn that moves on one axis. |
| "This control aims, but the pawn cannot fire. Aim is ignored." | Twin stick with a pawn that cannot fire. |
| "This control moves on one axis, so the pawn will too." | Two-way with a free-moving pawn. |
| "With one button the pawn moves on its own: A reverses, B stops." | One button with a free-moving pawn. |
| "Three hazards on a small board with a moving pawn: expect short games." | Three hazard chips, a pawn that always moves, and a board of 16 by 16 or smaller. |
| "Needs 2 seats. Link another cabinet, or it plays solo." | A game that needs two players (Last Standing, or Turns) with no computer opponent to fill a seat. |
| "Some settings are not used by this server and will be removed when you save." | A loaded cartridge with a setting this server does not know. |

Some combinations are legal and the bench cannot warn about them; the slot pages note them under "Odd
combinations".

## The pages

[Arena](Modules-Arena.md) · [Pawn](Modules-Pawn.md) · [Rule](Modules-Rule.md) · [Hazard](Modules-Hazard.md) ·
[Pickup](Modules-Pickup.md) · [Control](Modules-Control.md) · [Pace](Modules-Pace.md) ·
[Modifier](Modules-Modifier.md) · [Multiplayer](Modules-Multiplayer.md) · [Theme](Modules-Theme.md)
