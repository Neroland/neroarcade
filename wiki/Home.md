# NeroArcade Wiki

Player- and server-owner documentation for **NeroArcade**, part of the Neroland mod family. Built on
**Neroland Core**.

NeroArcade lets you make mini games inside Minecraft and play them with friends. A game is built from **module
chips**: ten slots, each deciding one thing — the board, what you steer, how you win, what threatens you, what you
collect, the controls, the speed, one twist, how many play, and the look. Put the chips in a **Game Bench**, try the
game, and save it to a **Cartridge**. Slide the cartridge into an **Arcade Cabinet** and play; everyone nearby can
watch the screen. Swap one chip and you have a different game.

Cartridges hold a game, not a program, so they are safe to share: copy them at a **Cartridge Duplicator**, publish
and download them at an **Arcade Exchange**, and remix someone else's game at your own bench. Join cabinets with a
**Link Bus** to play versus or co-op, and watch the scores on a **Marquee Screen**.

> **Status:** version `0.1.0-beta.1`, a beta. Everything below is built and tested in code; it has not yet had its
> first full run in a live game.

## Contents

### Playing

- [Getting Started](Getting-Started.md) — bench, chip, cartridge, cabinet in five steps
- [Modules](Modules.md) — the ten slots and every chip, with its settings and recipe
- [Building a Game](Building-a-Game.md) — three worked recipes from the example games
- [Cartridges and Sharing](Cartridges-and-Sharing.md) — save, copy, publish, download, remix, and credit
- [Multiplayer and the Link Bus](Multiplayer-and-the-Link-Bus.md) — linked cabinets, versus, co-op, turns
- [Leaderboards](Leaderboards.md) — top tens, personal bests, and who is named
- [Gallery](Gallery.md) — the showcase hall and the screenshot tool

### Reference

- [Commands](Commands.md) — every `/neroarcade` command and the two client commands
- [Config](Config.md) — every setting in `config/neroarcade.properties`
- [Privacy](Privacy.md) — what a server keeps about you, credit, export and erasure
- [NeroLink](NeroLink.md) — the companion app: your library, boards, notifications, export and import

## What is not in this version

No Holo-Arena (playing a game out in real blocks), tournaments, cross-server exchange, soundtrack chips or sprite
editors yet; no "challenge a friend" in the app; no turn-taking across linked cabinets; at most four players in
one game. Coin-op play with NeroEconomy is built but switched off by default. The full list is in the mod's
changelog.

## See also

- [Source code, build instructions, changelog and privacy notice](https://github.com/Neroland/neroarcade)
