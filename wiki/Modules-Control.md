# Modules: Control

The **Control** slot decides how the arcade buttons turn into movement and actions. One chip. Empty means
**Four-way**. Ingredient: **lever**. [All slots](Modules.md)

The arcade buttons are the pad (W A S D by default), **A** (Space), **B** (Left Shift) and the aim keys (arrow
keys). Rebind them under Controls → NeroArcade. The HUD's key legend always names the keys you bound.

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Four-way** (default) | Up, down, left and right to move, plus the A and B buttons. | Blank Chip + lever |
| **One button** | One action button and one alternate; the pawn moves on its own. | Blank Chip + lever + stone button |
| **Twin stick** | Move with the pad, aim with the stick, fire with A. | Blank Chip + lever + lever |
| **Two-way** | Left and right (or up and down) plus two buttons. | Blank Chip + lever + rail |

## Four-way

The pad moves the pawn; the last direction pressed wins unless diagonals are on.

| Setting | Default | Range |
| --- | --- | --- |
| Diagonals | off | on (8 directions), off (4 directions) |

## One button

The pad does nothing; only A and B count, and the pawn moves on its own. What A and B do depends on the pawn (for
example a Cursor reverses on A and stops on B; a Serpent turns left on A and right on B; a Runner hops on A).

| Setting | Default | Range |
| --- | --- | --- |
| Hold | off | on (holding a button keeps acting), off (each press acts once) |

## Twin stick

The pad moves in eight directions; the aim keys point the shot; A fires. Only a pawn that can fire (the Ship) uses
the aim; with any other pawn the bench warns that aim is ignored.

| Setting | Default | Range |
| --- | --- | --- |
| Aim | Eight-way | Eight-way, Free |

## Two-way

Movement on one axis only, plus A and B. A free-moving pawn then moves on that axis too (the bench warns).

| Setting | Default | Range |
| --- | --- | --- |
| Axis | Horizontal | Horizontal (left-right), Vertical (up-down) |
