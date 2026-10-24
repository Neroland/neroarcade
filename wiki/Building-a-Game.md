# Building a Game

Three complete games, built chip by chip. They are three of the sixteen example games: in a creative world the
[Gallery](Gallery.md)'s Arcade Exchange lists all sixteen, so you can download them and compare. Rows not listed
are left **empty** and use their default. A setting not mentioned stays at its default. See
[Modules](Modules.md) for every chip.

## 1. Serpent Garden — a one-player growing game

Steer a serpent round a garden that wraps at the edges, eat orbs, and reach 100 points without biting yourself.

| Slot | Chip | Settings |
| --- | --- | --- |
| Arena | Grid 16 *(or leave empty)* | — |
| Pawn | Serpent | Speed 2 |
| Rule | Score Target | Target 100, Lives 1 |
| Pickup | Score Orb | (3 orbs worth 10) |
| Control | Four-way *(or leave empty)* | — |
| Modifier | Wrap-around | Axes Both |
| Theme | Terminal Green | — |

Why it works: Score Target needs points, and the Score Orbs give them (each orb comes back after a second); every
orb also makes the serpent longer, so the game gets harder as you get closer to winning. Wrap-around opens the
border, so the only thing that can hurt you is your own tail.

Try next: set the Serpent's Speed to 3 for a faster game; add a **Chaser** in the Hazard row and the board gets a
hunter; swap Score Target for **Survive** with 90 seconds and the orbs become a way to grow, not to win.

## 2. Vault Run — keys, a maze and drones

Find three keys in a big maze while two drones hunt you, then reach the exit before your lives run out.

| Slot | Chip | Settings |
| --- | --- | --- |
| Arena | Grid 32 | Obstacles Light maze |
| Pawn | Cursor *(or leave empty)* | — |
| Rule | Reach Exit | Needs key on (Lives 3) |
| Hazard | Chaser | Count 2 |
| Pickup | Key | Count 3 |
| Theme | Terminal Green | — |

Why it works: with **Needs key** on, the exit appears locked on the far side of the board and opens only once all
three keys are collected. Keys are placed away from where you start and never come back. The drones always step
toward you through the maze, so you have to plan a route. After losing a life you get a one-second shield.

Try next: add a **Shield** pickup to give yourself breathing room; add **Fog** in the Modifier row (radius 5) and the
maze is only visible around you; turn **Needs key** off and it becomes a straight race to the exit.

## 3. Paddle Duel — two players, one ball

A two-player game for two linked cabinets: keep the ball from crossing your goal line. Three misses and you are out.

| Slot | Chip | Settings |
| --- | --- | --- |
| Arena | Grid 16 *(or leave empty)* | — |
| Pawn | Paddle | Length 3, Speed 4 |
| Rule | Last Standing | Lives 3 |
| Hazard | Bouncing Ball | (Speed 3) |
| Control | Two-way | Axis Horizontal |
| Multiplayer | Versus | Seats 2 |
| Theme | Neon Void *(or leave empty)* | — |

Why it works: each Paddle paints its player's goal line behind it. The ball bounces off walls and paddles; a ball
past your goal line costs you a life and gives your opponent a point, then a new ball is served. Last Standing ends
the match when one player is left. Two-way keeps the paddle on its line, so the pad's other axis does nothing.

To play it, build a Link Bus with two cabinets (see [Multiplayer and the Link Bus](Multiplayer-and-the-Link-Bus.md)),
put the cartridge into one of them, and have both players sit down and press A when ready. Alone, the game waits ten
seconds and then plays you against the computer.

Try next: Count 2 on the Bouncing Ball for chaos; Seats 4 on Versus and four linked cabinets — players three and
four guard the left and right sides; swap Last Standing for **Score Target** (Target 5) to play to points.

## Tips

- The rack row and the preview update as you change a setting; TEST PLAY before you save.
- A grey ▲ warning is advice, not an error.
- A saved game shows its chips in the cartridge tooltip, and `/neroarcade cartridge inspect` (holding it) lists
  every slot.
