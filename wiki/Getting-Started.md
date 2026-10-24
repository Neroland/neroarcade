# Getting Started

From nothing to your own game on a cabinet in five steps. You need Neroland Core installed (it provides Nero Alloy,
used in most NeroArcade recipes). Every recipe is also in the recipe book and in a recipe viewer.

## 1. Craft a Game Bench and some chips

| Item | Recipe |
| --- | --- |
| **Game Bench** | Three glass panes on top; Nero Alloy, redstone, Nero Alloy in the middle; three iron ingots below. |
| **Blank Chip** (×4) | Copper ingot, redstone, copper ingot in a row. |
| **Module Chip** | A Blank Chip plus its slot's ingredient, plus one more item for most chips (shapeless). |

The seven simplest chips need only the Blank Chip and the slot's ingredient: a glass pane makes **Grid 16**, Nero
Alloy makes **Cursor**, paper makes **Survive**, a lever makes **Four-way**, a repeater makes **Steady**, a copper
ingot makes **Solo** and pink dye makes **Neon Void**. Each chip's full recipe is on its [Modules](Modules.md) page.

You do not need a chip for every slot. An empty slot uses its default, so a bench with no chips at all already makes
a playable game: a cursor on a 16 by 16 board that has to survive for 60 seconds.

## 2. Put chips in the bench and try the game

Place the bench and use it. The screen ("Build your game") has ten rows, one per slot, in a fixed order: Arena,
Pawn, Rule, Hazard, Pickup, Control, Pace, Modifier, Multiplayer, Theme. Put a chip into its row's slot. The Hazard
and Pickup rows take up to three chips each (a `+` shows where another fits).

- Each row says in one line what its chip does, with the current values ("Survive 60 s · lives 1"). An empty row
  says which default it uses. Hover a row for the full description.
- **Click a row** to change its chip's settings with sliders, switches and choice buttons.
- A grey line with ▲ is a **warning**: the game still works, but something is odd ("Nothing can hurt the pawn, so
  this is won by waiting."). Warnings never stop you saving.
- The **preview** plays your game on its own as you change it.
- **TEST PLAY** lets you play it right there at the bench with your arcade keys; **STOP TEST** (or Esc) ends it.

Chips are templates: they stay in the bench and are never used up. A button you cannot press says why when you
hover it.

## 3. Name it and save it to a cartridge

Craft **Blank Cartridges** (Nero Alloy, copper ingot, Nero Alloy on top; copper ingot, redstone, copper ingot
below; makes two) and put one in the bench's tray. Type a title (up to 32 characters; the server's chat filter
checks it) and press **SAVE TO CARTRIDGE**. The blank becomes a **Cartridge** carrying your game: its tooltip shows
the title, the chips and who built it ("by Anonymous builder" unless you turned credit on — see
[Cartridges and Sharing](Cartridges-and-Sharing.md)).

Put your own cartridge back in the tray and SAVE again to update it in place (its revision goes up). **LOAD** puts a
cartridge's chips back into the rack so you can change it.

## 4. Place a cabinet and insert the cartridge

| Item | Recipe |
| --- | --- |
| **Arcade Cabinet** | Iron ingot, glass pane, iron ingot on top; Nero Alloy, block of redstone, Nero Alloy in the middle; three iron ingots below. |

The cabinet is two blocks tall. Use it while holding the cartridge to slide the cartridge in ("Cartridge in."). Its
status strip turns cyan (READY) and the screen starts an attract loop: the title, a demo, the high scores.
Sneak-use the cabinet to take the cartridge out again.

## 5. Sit down and play

Use the cabinet with an **empty hand**. A one-player game starts straight away with a 3-2-1 countdown. While you
are seated the cabinet takes your movement keys and you stand still:

| Key (default) | Arcade button |
| --- | --- |
| W A S D | the pad (move) |
| Space | A (action; also START in a lobby and on the results card) |
| Left Shift | B (second action) |
| Arrow keys | aim (twin-stick games) |
| Esc | leave the seat |

Rebind them under Controls → NeroArcade. The HUD shows the goal, your score, the timer and, for the first few
seconds, which keys do what. When the match ends the results card shows your rank and score, and **NEW BEST** if you
beat your personal best. Press A to play again or Esc to leave. Walking more than 3 blocks away also gives the seat
back.

Anyone within 32 blocks in front of the cabinet sees the same screen. Nobody else sees your HUD.

## Next

- [Building a Game](Building-a-Game.md) — three complete games to build.
- [Cartridges and Sharing](Cartridges-and-Sharing.md) — copy, publish, download and remix.
- [Multiplayer and the Link Bus](Multiplayer-and-the-Link-Bus.md) — play together.
- `/neroarcade top` shows the leaderboard of the cartridge in your hand or the cabinet you are looking at.
