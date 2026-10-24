# Modules: Multiplayer

The **Multiplayer** slot decides how many play and whether they team up, compete or take turns. One chip. Empty means
**Solo**. Ingredient: **copper ingot**. [All slots](Modules.md)

More than one player needs more than one seat: join cabinets with a Link Bus (see
[Multiplayer and the Link Bus](Multiplayer-and-the-Link-Bus.md)). Turns is the exception — it is played at one
cabinet.

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Solo** (default) | One player has the whole board. | Blank Chip + copper ingot |
| **Co-op** | Two to four players share one board and, by default, one life pool. | Blank Chip + copper ingot + copper ingot |
| **Turns** | Players take turns at one cabinet; pass the controls when told. | Blank Chip + copper ingot + clock |
| **Versus** | Players compete on one board; last one standing or best score wins. | Blank Chip + copper ingot + iron sword |

## Solo

One player. No settings.

## Co-op

Everyone plays on the same board toward the same goal. With **Shared lives** on, the team has one pool of lives;
off, each player has their own. Scores are kept per player.

| Setting | Default | Range |
| --- | --- | --- |
| Seats | 2 | 2–4 |
| Shared lives | on | on, off |

## Versus

Everyone plays on the same board against each other. The last pawn standing wins; when the rule names several
winners at once, the highest score among them wins (a tie keeps both). If everyone goes down together it is a
draw. A Versus game with one player and a free linked cabinet waits ten seconds for a second player, then brings in
the computer opponent.

| Setting | Default | Range |
| --- | --- | --- |
| Seats | 2 | 2–4 |

## Turns

Hot-seat play at the cabinet that holds the cartridge: one set of controls, and the board changes hands every turn.
The HUD says "P2's turn — pass the controls". A player whose turn it is not is off the board. A Turns game is never
recorded on a leaderboard, because one seat cannot tell who held the controls.

| Setting | Default | Range |
| --- | --- | --- |
| Seats | 2 | 2–4 players |
| Turn seconds | 30 | 10–120 |

## Limits in this version

- At most four players in one game.
- Turns cannot be played across linked cabinets.
