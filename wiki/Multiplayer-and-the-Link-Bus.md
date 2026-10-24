# Multiplayer and the Link Bus

A cabinet on its own seats one player. To play together, join cabinets into a **bus**: up to four cabinets play
**one** game, run by the cabinet that holds the cartridge. Every player watches the same board.

## The blocks

| Item | Recipe | What it does |
| --- | --- | --- |
| **Link Bus** | Copper ingot, Nero Alloy, copper ingot; Nero Alloy, redstone, Nero Alloy; copper ingot, Nero Alloy, copper ingot. | The hub that joins up to four cabinets, through up to 16 Link Cables, into one game. |
| **Link Cable** (×6) | String, copper ingot, string in a row. | Connects cabinets and Marquee Screens to a Link Bus. Glows while a linked game is on. |
| **Marquee Screen** | Three glass panes; glass pane, glowstone dust, glass pane; three iron ingots. | A big screen for spectators. |

## Building a bus

1. Place a **Link Bus**.
2. Run **Link Cables** from it to each cabinet (and to any Marquee Screen). Cables connect to cables, the bus,
   cabinets and marquees.
3. Put the cartridge into **one** cabinet. That cabinet is the **host**: it runs the game. The other cabinets show
   the same screen and each seat one more player.

Limits: four cabinets and 16 cables per bus. A fifth cabinet shows **BUS FULL**. A second cartridge on the same bus
shows **LINKED — REMOVE CARTRIDGE** (sneak-use that cabinet to take it out). A linked cabinet with no cartridge on
the bus shows **LINKED — WAITING**. You cannot put a cartridge into a linked cabinet or into a cabinet someone is
sitting at.

How many players a game takes is set by its [Multiplayer chip](Modules-Multiplayer.md): Solo uses only the host,
Co-op and Versus seat 2–4 players, Turns is played at the host only.

## Playing

1. Each player uses a different cabinet on the bus with an empty hand to take a seat.
2. The lobby says "Waiting for players — 1 of 2 seated" and then "Press Space when you are ready" (your A key).
3. When everyone who sat down is ready and the game has the players it needs, the 3-2-1 countdown starts.
4. If not every seat is taken, the game starts ten seconds after everyone present is ready; the lobby shows
   "Starting in N s". A **Versus** game with one player brings in the computer opponent; a **Co-op** game with one
   player is played solo.
5. After the match the results stay up for ten seconds; then "Ready for another go?".

You hold one seat on the whole server at a time. Your seat is released when you press Esc, walk more than 3 blocks
from your cabinet, log out, or the cabinet is broken. Leaving during a match keeps the match going for the others
where the rule allows it (Last Standing, for example, puts the last player on the solo clock).

While anyone sits on a bus, only a seated player or an operator can take the host's cartridge out. Placing or
breaking blocks beside a running bus does not drop anyone; a cabinet added during a match joins at the next one.

## When things break

- Breaking the **host cabinet**, the **Link Bus** or a **cable** during a match ends it as **MATCH ABANDONED** for
  everyone. Nothing is recorded.
- Breaking any other cabinet only frees its seat.
- If the host's chunk unloads, every seated player gets their controls back at once.

## The Marquee Screen

On a bus, a Marquee Screen shows the game being played with every player's score and the timer, and rolls the
cartridge's top ten between games. Without a bus it shows the NeroArcade card. Sneak-use it to change its size
(1 to 4); the picture grows, the block stays one block.

## Spectators

Anyone within 32 blocks in front of a cabinet or marquee sees its screen. Spectators see no HUD and no names. A
cabinet with nobody near goes to sleep after a few seconds and wakes when someone comes back.

## Hot-seat Turns

A **Turns** game is played at the host cabinet by passing one set of controls around: the HUD says "P2's turn — pass
the controls". It is never recorded on a leaderboard. Turns across linked cabinets is not in this version.

## For server owners

- `multiplayer.enabled=false` makes every Link Bus join nothing; every cabinet then plays on its own.
- `runtime.maxAwakeCabinets` (32) caps how many cabinets run a game at once; further ones show "ARCADE BUSY" and
  queue. A whole bus counts once.
- `/neroarcade admin awake` shows how many games run now, of the cap, and how many wait.
- `/neroarcade admin unseat <player>` frees a player's seat.
- See [Config](Config.md) and [Commands](Commands.md).
