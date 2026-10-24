# Modules: Arena

The **Arena** slot decides the board: its size, its walls, and whether it scrolls or wraps. One chip. Empty means
**Grid 16**. Ingredient: **glass pane**. [All slots](Modules.md)

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Grid 16** (default) | A 16 by 16 board with a wall border and optional obstacles. | Blank Chip + glass pane |
| **Grid 32** | A 32 by 32 board with a wall border and optional obstacles. | Blank Chip + glass pane + glass pane |
| **Ring 24** | A 24-wide tube that wraps left to right, with optional pillars. | Blank Chip + glass pane + chain |
| **Strip 9** | A 9-wide lane that can scroll new wall gates toward the pawn. | Blank Chip + glass pane + rail |

## Grid 16

The house board. A wall runs round the edge.

| Setting | Default | Choices |
| --- | --- | --- |
| Obstacles | None | None, Pillars (a pillar every four cells), Light maze (a few short wall bars placed by the match's seed) |

## Grid 32

The same board at four times the area. Light maze places more and longer bars than on Grid 16.

| Setting | Default | Choices |
| --- | --- | --- |
| Obstacles | None | None, Pillars, Light maze |

## Ring 24

A board 24 cells wide and 8 tall with a wall along the top and bottom. The left and right edges join: leave one
side and you come back on the other. Up to four players start spread round the ring.

| Setting | Default | Choices |
| --- | --- | --- |
| Obstacles | None | None, Pillars (short wall pairs across the middle) |

## Strip 9

A lane 9 cells wide with no walls inside; the board's edge is the wall. With scrolling on, the lane moves one row at
a time and new rows arrive at the far end: half are open, the rest have a gate of one to three wall cells (never
more than three of nine blocked, never two gated rows in a row). A pawn caught by an arriving wall loses a life.

| Setting | Default | Range |
| --- | --- | --- |
| Scroll | None | None, Down, Up |
| Period (ticks per row) | 10 | 4–40 |
| Length | 24 | 16–32 rows |

## Odd combinations

- A 32 by 32 maze with a speed-1 cursor is slow, but it plays.
- A scrolling Strip 9 with the Stacker pawn scrolls the stack too.
- A scrolling Strip 9 with the Paddle pawn scrolls the goal lines away — it runs, but it is a joke game.
- A Paddle on Ring 24: the ball wraps left and right, so it only scores at the top and bottom.
