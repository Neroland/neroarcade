# Modules: Pawn

The **Pawn** slot decides what each player steers and how it moves. One chip; every player in a game gets the same
pawn. Empty means **Cursor**. Ingredient: **Nero Alloy**. [All slots](Modules.md)

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Cursor** (default) | A free cursor that moves in four directions and stops at walls. | Blank Chip + Nero Alloy |
| **Paddle** | A slide-along bar that bounces hazards and shots back. | Blank Chip + Nero Alloy + stick |
| **Runner** | Runs on its own; tap to hop over hazards, steer to change lane. | Blank Chip + Nero Alloy + leather boots |
| **Serpent** | Always moving; grows with each pickup and dies touching itself. | Blank Chip + Nero Alloy + slime ball |
| **Ship** | Drifts with inertia and fires shots where it points. | Blank Chip + Nero Alloy + arrow |
| **Stacker** | Guide falling pieces; full rows clear and score. | Blank Chip + Nero Alloy + brick |

Any pawn that stands on a hole in the floor (see Shrinking Floor) loses a life. After losing a life a pawn comes
back with a short shield (one second) so nothing can wait for it at the spot where it returns.

## Cursor

Moves one way at a time with the pad and stops at walls. With the **One button** control it moves on its own
(starting to the right): A reverses it, B stops it.

| Setting | Default | Range |
| --- | --- | --- |
| Speed | 3 | 1–5 |

## Paddle

A bar that slides along one line near the edge of the board. Behind it is the player's **goal line**: a hazard that
reaches your goal line costs you a life. The paddle bats balls, crates and shots back instead of being hurt by
them; a moving paddle puts a little spin on a ball. Players one and two take the chosen axis (bottom and top, or
right and left); players three and four take the other.

| Setting | Default | Range |
| --- | --- | --- |
| Length | 3 | 1–8 cells |
| Speed | 3 | 1–5 |
| Axis | Horizontal | Horizontal, Vertical |

## Runner

Runs along its lane on its own and turns round at a wall. Left and right (or the pad's one axis) move it one lane
over per press. **A** hops: while in the air it passes over hazards and shots and can clear a hole, but not a wall.
Landing locks the next hop and lane change for four ticks. Low Gravity makes hops last longer.

| Setting | Default | Range |
| --- | --- | --- |
| Speed | 3 | 1–5 |
| Jump (cells) | 2 | 1–4 |

## Serpent

Always moving. The pad turns it (it cannot turn straight back on itself); with the One button control, A turns left
and B turns right. Every pickup makes it one segment longer (up to 48). Running into a wall, a solid thing or its
own body costs a life. In a game with several serpents, only the one whose head runs into another's body is hurt;
head-on, both are.

| Setting | Default | Range |
| --- | --- | --- |
| Speed | 3 | 1–5 |
| Start length | 3 | 1–8 |

## Ship

Thrust pushes it and it keeps drifting; drag slows it down. It faces the way it last moved. **A** fires a shot
(at most one every half second) along the aim stick if the control has one, otherwise the way it faces. Shots fly
for two seconds or until they hit a wall; a shot can take down a Chaser drone (two hits) and scores for the shooter.
A paddle can bat a shot back, and the batted shot then belongs to the paddle's player.

| Setting | Default | Range |
| --- | --- | --- |
| Thrust | 3 | 1–5 |
| Drag | Light | None, Light, Heavy |
| Fires | on | on, off |

## Stacker

Pieces of two to four blocks fall from the top. Left and right shift the piece, **A** turns it, **B** drops it at
once, and holding down makes it fall faster. A landed piece becomes part of the stack. A full row clears and
scores *row bonus × rows²* (two rows at once score four times one row); every landed piece scores 1. A new piece that
cannot fit at the top ends the game for that player. A falling crate that hits the piece costs a life.

| Setting | Default | Range |
| --- | --- | --- |
| Fall (rows per second) | 1 | 1–5 |
| Shapes | Mixed | Bars (two- and three-block bars), Mixed (bars, a corner and a square) |
| Row bonus | 10 | 1–100 |

## Odd combinations

- A Paddle with the Reach Exit rule: the exit is placed at the far end of its line, which it cannot reach — the
  match runs to its time limit.
- A Runner on Grid 16 shuttles up and down one column. It plays, but it is dull.
- Two Stackers in a Versus game share one board; a cleared row scores for whoever landed the last piece.
- A Stacker with Laser Sweep, or with Fog, plays — and is hard.
- Two idle Paddles can return a ball that comes off a corner along the same diagonal for ever; the match then ends
  at its time limit.
