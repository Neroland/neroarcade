# Modules: Pace

The **Pace** slot decides how fast the game runs and whether that changes during a match. One chip. Empty means
**Steady** at full speed. Ingredient: **repeater**. [All slots](Modules.md)

A game runs at 20 steps a second at full speed. A **slow-down** (or divisor) of 2 runs it at half that, 4 at a
quarter. The clock always counts real seconds.

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Steady** (default) | The game runs at one constant speed. | Blank Chip + repeater |
| **Pulse** | Alternates bursts of full speed with slow stretches. | Blank Chip + repeater + redstone torch |
| **Ramp** | Starts slow and speeds up to full speed in steps. | Blank Chip + repeater + redstone |

## Steady

Slow-down 1 is full speed; 4 is a quarter of it.

| Setting | Default | Range |
| --- | --- | --- |
| Slow-down | 1 | 1–4 |

## Pulse

Full speed for the fast stretch, then the slow divisor for the slow stretch, over and over.

| Setting | Default | Range |
| --- | --- | --- |
| Fast (ticks) | 60 | 20–200 |
| Slow (ticks) | 60 | 20–200 |
| Slow divisor | 2 | 2–4 |

## Ramp

Starts at the start divisor and steps one closer to full speed at every interval, until it reaches full speed.

| Setting | Default | Range |
| --- | --- | --- |
| Start divisor | 2 | 1–4 |
| Every (seconds) | 20 | 5–120 |
