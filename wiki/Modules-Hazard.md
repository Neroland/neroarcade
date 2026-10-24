# Modules: Hazard

The **Hazard** slot decides what threatens the pawns. Up to **three** chips (the same chip twice with different
settings is fine). Empty means no hazard. Ingredient: **gunpowder**. [All slots](Modules.md)

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Bouncing Ball** | A ball that bounces off walls and bats and scores past a goal line. | Blank Chip + gunpowder + snowball |
| **Chaser** | A drone that walks toward the nearest pawn through the maze. | Blank Chip + gunpowder + rotten flesh |
| **Falling Blocks** | Crates drop from the top at random; dodge or catch them. | Blank Chip + gunpowder + cobblestone |
| **Laser Sweep** | A beam with one gap sweeps back and forth; slip through the gap. | Blank Chip + gunpowder + glowstone dust |
| **Shrinking Floor** | The outer ring of the floor falls away at a steady interval. | Blank Chip + gunpowder + sand |

Touching a hazard costs a life unless the pawn has a shield, is a Paddle (it bats things back), or is a Runner in the
air.

## Bouncing Ball

Balls are served from the middle after a two-second pause and fly diagonally, bouncing off walls and paddles. A
ball that crosses a player's goal line (see the Paddle pawn) costs that player a life and gives every other player
a point, then a new ball is served. Without paddles it is simply a ball to dodge.

| Setting | Default | Range |
| --- | --- | --- |
| Count | 1 | 1–4 balls |
| Speed | 3 | 1–5 |

## Chaser

Drones that step toward the nearest pawn through the maze, taking the shorter way round on a board that wraps. They
do not turn straight back unless they must, and they keep off goal lines. A drone that is shot twice is destroyed
and gives the shooter 5 points; a missing drone returns after three seconds.

| Setting | Default | Range |
| --- | --- | --- |
| Count | 1 | 1–4 drones |
| Speed | 2 | 1–5 |

## Falling Blocks

Crates appear at random along the top and fall. A crate that reaches a paddle's goal line costs that player a life;
a paddle that **catches** a crate scores a point. Low Gravity makes crates fall more slowly.

| Setting | Default | Range |
| --- | --- | --- |
| Rate (per 32 ticks) | 4 | 1–10 (the chance, out of 32, that a crate appears each tick) |
| Speed | 2 | 1–5 |

## Laser Sweep

A beam across the board sweeps from one side to the other and back. It starts on the line farthest from every
player, and on every pass it carries a **gap** (a quarter of its length, at least two cells) in a new place: slip
through the gap or cross behind the beam. The beam never moves faster than one cell every two ticks. Shots cannot
destroy it, and a paddle cannot bat it.

| Setting | Default | Range |
| --- | --- | --- |
| Period (ticks per sweep) | 80 | 20–200 |
| Axis | Rows | Rows (a horizontal beam), Columns (a vertical beam), Both |

## Shrinking Floor

At every interval the outermost ring of open floor becomes a hole, until the open area is down to the minimum size.
A pawn standing on a hole loses a life. Walls, exits and goal lines stay. There is no warning flash: the interval is
the warning.

| Setting | Default | Range |
| --- | --- | --- |
| Interval (ticks) | 100 | 20–400 |
| Minimum size | 4 | 2–16 cells across |

## Odd combinations

- A Bouncing Ball on Ring 24 wraps left and right, so it can only score at the top and bottom.
- A Laser Sweep with period 20 on a board with a speed-1 Cursor is legal and very hard; the bench cannot warn about
  it.
- A Stacker with Shrinking Floor: pieces fall through holes to the board's edge.
- Pickups left on a hole by Shrinking Floor cannot be collected.
