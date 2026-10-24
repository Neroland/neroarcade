# Leaderboards

Every game has a **top-ten board**, and every player who finishes a match on it has a **personal best**.

## One board per cartridge id

A board belongs to a cartridge's **id**:

- **Copies and downloads share the board.** A cartridge copied at the Duplicator or downloaded from the Exchange is
  the same game with the same id, so its scores land on the same board, wherever it is played on the server.
- **Your own updates keep the board.** Re-saving your own cartridge in place keeps its id and its board.
- **Remixes start fresh.** A remix (or any new save onto a Blank) is a new game with a new id and an empty board —
  so a remix with easier rules can never post onto the original's board.

Boards are per server; there are no cross-server boards.

## What gets recorded

- Only the cabinet running the match writes to the board, once, when the match finishes. Nothing a player's game
  client sends can write a score.
- The results card marks **NEW BEST** when you beat your personal best.
- A player who walks out of a match keeps a personal best but never takes a top-ten place.
- **Abandoned** matches (the host cabinet, Link Bus or a cable broken mid-match) and hot-seat **Turns** matches
  record nothing.
- A server owner can switch recording off (`leaderboards.enabled=false`); existing boards stay and are still shown.

## Seeing a board

- `/neroarcade top` — the board of the cartridge in your hand, or of the cabinet you are looking at. It also shows
  your own personal best.
- A **Marquee Screen** on a bus rolls the game's top ten between matches.
- An idle cabinet's attract loop shows the high scores.
- The NeroLink app shows the boards where you have a best (see [NeroLink](NeroLink.md)).

## Names only with consent

A row shows a player's Minecraft name only if that player turned credit on (`/neroarcade privacy credit on`) when the
row was written. Everyone else is **Anonymous** ("ANON" on the cabinet screens). Turning credit off removes your name
from every row on the server at once. A name on a row is the name at the time; if you change your Minecraft name the
old one stays on that row until it expires or you turn credit off. Your personal best is shown only to you. See
[Privacy](Privacy.md).

## How long scores are kept

- **Top-ten rows: 90 days** after they were set. A server owner can shorten this (`privacy.leaderboardRetentionDays`)
  but never make it longer than a year.
- **Personal bests: one year** after you set them; beating your best starts the year again
  (`privacy.personalBestRetentionDays`, at most a year).
- An erasure request removes your rows and bests at once.
