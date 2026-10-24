# NeroArcade privacy & telemetry disclosure

NeroArcade lets players build, play and share mini games inside Minecraft. This page lists everything it stores
about players, why, for how long, and how a player can see, limit or erase it. It is the full disclosure for the
GDPR (EU) and POPIA (South Africa), and the disclosure CurseForge's moderation rules ask of mods that contain an
external error-reporting service.

## At a glance

- **Stored about you:** your player UUID as the author of games you save, your **name only if you opt in** to
  credit, your personal bests and top-ten leaderboard rows, the Exchange listings you publish, the drafts you import
  from the companion app, and whether you turned credit on.
- **Never stored:** your inputs beyond the match being played (not saved, not logged), a history of which games you
  played or when, who hearted or downloaded what, chat, positions of players, IP addresses.
- **Your choices, in game:** `/neroarcade privacy credit on|off` (be named, or not; off by default),
  `/neroarcade privacy export` (get a copy), `/neroland data eraseme` (erase).
- **Crash reports:** anonymous, EU servers, **on by default**; opt out with `telemetryEnabled=false`. See
  [Crash reports](#crash-reports-telemetry).

## What NeroArcade stores, and what it does not

### Cartridges (items)

A **cartridge** is an item that holds one game. When you save a game at a Game Bench, the cartridge records:

- the game itself (its chips, settings and the title you type);
- your **player UUID**, always, as the cartridge's author. It is never shown anywhere: not in the tooltip, not in
  `/neroarcade cartridge inspect`, not on any screen, never sent to other players' games. It exists so "re-save
  your own cartridge", unpublishing and erasure can recognise your work;
- a **credit name** only if you had credit turned on when you saved. Otherwise the cartridge says "by Anonymous
  builder" and carries no name.

Copies made with the Cartridge Duplicator, and downloads from the Arcade Exchange, carry the author UUID and the
credit exactly as they were saved. Cartridges are tagged `neroeconomy:untradeable` so economy mods do not list them
for sale.

#### The inventory-item limit

A cartridge is an item. It lives in inventories, chests, item frames and the blocks below like any other item, and
the server cannot list or rewrite items it is not holding in a store. **An erasure request therefore cannot reach
cartridges held by other players or stored in the world.** The mitigation is that credit is opt-in and off by
default, so a cartridge carries your name only if you chose that; the UUID on it is never displayed or sent to other
players.

The same applies to these **places in the world save (block data)** that can hold a cartridge or its author:

| Block | What it keeps | Reached by erasure? |
|---|---|---|
| Arcade Cabinet | the inserted cartridge item (with its author UUID and credit). Other players' games receive only the title, theme colour, whether it is readable and the game's chips — never the author or the credit. | no (item) |
| Cartridge Duplicator | the items in its four slots | no (items) |
| Game Bench | the cartridge in its tray (item), **and its draft's origin: the author UUID and credit of the cartridge last LOADed into it**, until the next LOAD or SAVE there replaces them. Never shown or sent: the bench preview and update tag strip the author and the credit | the draft origin: **yes, for every bench loaded at the time** (author replaced by an anonymous marker, credit cleared). A bench in an unloaded chunk keeps it until its next LOAD/SAVE |
| Arcade Exchange kiosk | nothing. The tray is per player and per open screen; whatever is in it returns to you when you close the kiosk. (A kiosk saved by a very early build drops its old item on the next load.) | — |

### Seats and matches (memory only)

When you sit at a cabinet (or test a game at a bench), the server remembers **which cabinet you sit at, in memory
only**. It is never written to disk, exported or logged, and is forgotten when you leave the seat, walk away, log out
or the server stops. Your inputs drive the match while it runs and are discarded with it. An erasure request
unseats you straight away, and a match you were in records nothing for you when it ends.

Other short-lived, memory-only lists hold player UUIDs for the current session and are never saved: per-player rate
limits at the Game Bench and the kiosk, who hearted which listing this session, which nearby players were last sent a
cabinet's screen or a marquee's board (replaced every update), which players are online (for quest-pack gates), and
the export cooldown. The first four and the export cooldown are cleared by an erasure request; the screen and board
lists hold only players standing in range right now; all of them are gone when the server stops.

### Leaderboards

When a match ends normally, the server keeps, per cartridge (a board belongs to one cartridge id: copies and
downloads of a game share its board, a remix gets a fresh board):

- **top-ten rows**: your player UUID, your score, when it was set, and your name **only if you had credit turned on
  at that moment** (otherwise the row says "Anonymous");
- **your personal best**: your player UUID, your best score on that cartridge, and when you set it.

Nothing else: not which cabinet you used, not who you played with, not every game you played. Abandoned matches
and hot-seat games record nothing; a player who walks out keeps their personal best but never gets a top-ten row.
Other players see ranks, scores and opted-in names only, never a UUID; your personal best is shown only to you.

### Credit opt-in

`/neroarcade privacy credit on` lets NeroArcade show your Minecraft name; `/neroarcade privacy credit off` takes it
back. **It is off by default.** While it is on, games you save and new leaderboard rows carry your name. Turning it
off removes your name straight away from every leaderboard row, every Exchange listing and draft, and every loaded
Game Bench on the server;
turning it on never adds your name to older rows. The server stores only that you turned credit on (your UUID, no
time, no name); turning it off deletes that row.

### The Arcade Exchange

When you publish a game at a kiosk, the server keeps one **listing**: the game (including its author's UUID, never
shown or sent to other players), a listing number, when it was first published, its genre words, the credit **only
if credit was on when you published**, and two counts:

- **plays**: one number that goes up when a cartridge of that game finishes its first match after being put into a
  cabinet. It records no player, place or time. It is a signal, not a score.
- **hearts**: one number. To stop double hearts the server remembers who hearted what **in memory only, for the
  current session** (and limits everyone to 20 hearts an hour); that memory is never saved, exported or logged.

Only the author or an operator can unpublish a listing, and only the author or an operator can re-publish over it
(a cartridge carrying the same game id under a different author is refused). The Exchange holds at most
`privacy.exchangeMaxListings` (200) listings and one player at most `privacy.listingsPerAuthor` (16; operators
exempt); nothing is evicted when it is full. `/neroarcade admin unpublish <player>` removes every listing one player
published.

**Drafts:** games you import from the NeroLink companion app wait in your own drafts at the Exchange, which only you
can see: at most `privacy.draftsPerPlayer` (8), each deleted **30 days** after it arrived or when you fetch it.

### Coin-op (optional)

If a server installs NeroEconomy and turns on `economy.coinOp` (off by default), a cabinet seat costs a few credits
and a download may pay the author a cut. NeroArcade passes only the player UUIDs involved to NeroEconomy for the
payment and records nothing about it itself; an author whose data was erased is never paid.

### What NeroArcade does not store

- no play history: `privacy.playHistory` is always `false` and no code path keeps one;
- no inputs after the match, no replays of real players' matches (the attract-mode demos are recordings shipped with
  the mod);
- no chat (titles pass the server's own chat filter and are stored only as the title);
- no positions of players, no IP addresses, no names unless you opted in.

## Legal basis

- Leaderboard rows, personal bests, Exchange listings and drafts, author UUIDs on games, the seat and the rate
  limits: **legitimate interest** in running the arcade fairly (knowing whose game it is, who may change or
  unpublish it, showing scores) using the minimum data (GDPR Art. 6(1)(f); POPIA s11(1)(f)), balanced by the short
  retention, the caps, export and erasure, and UUIDs never being shown.
- Your **name** on games and boards (credit): **consent** (GDPR Art. 6(1)(a); POPIA s11(1)(a)). Off by default,
  withdrawn at any time with `/neroarcade privacy credit off`, which removes the name from everything the server
  holds.
- Crash reports: legitimate interest in keeping the mod working (GDPR Art. 6(1)(f); POPIA s11(1)(f)).

## Retention

| Data | Kept | Setting |
|---|---|---|
| Top-ten leaderboard rows | 90 days by default, **never more than 365** | `privacy.leaderboardRetentionDays` (1 to 365) |
| Rows per board | 10 by default | `privacy.leaderboardMaxEntries` |
| Personal bests | 365 days after you set them (beating your best restarts the clock; playing without beating it does not) | `privacy.personalBestRetentionDays` (1 to 365) |
| Exchange listings | until unpublished or erased; at most 200 | `privacy.exchangeMaxListings` |
| Drafts | 30 days | fixed |
| Credit opt-in | until you turn it off or erase | — |
| Export files | 7 days | fixed |
| Seats, rate limits, hearts memory, link events | memory only, until the server stops (or sooner) | — |

A larger value in `config/neroarcade.properties` is clamped to the cap. The purge runs on the first server tick
after a world loads and then every hour. A personal best saved by a version before this one starts its clock at
the first purge.

## Your choices: be named, get a copy, be erased

**Credit (opt-in).** `/neroarcade privacy credit on|off`, described above. `/neroarcade privacy credit` on its own
tells you which applies.

**Export.** `/neroarcade privacy export` writes one JSON file, `neroarcade/exports/<your uuid>.json` in the world
folder, and tells you its path in chat; ask the server operator for it. It holds: your credit setting and the
retention settings in force; each board where you hold a personal best (the cartridge id, your best, when you set
it, and your top-ten row's rank, score, time and the name it carries, if you have one); every Exchange listing you
published or authored (number, cartridge id, title, revision, when published, plays, hearts, genre words, credit,
and `you_are`, which is always `author` in this version (a listing's author is the game's author)); your drafts (cartridge id, title, when imported, when they will
be deleted); and whether you are seated now and how many listings you hearted this session (a count, never which).
It never contains another player's id, name or data. One export per `privacy.exportCooldownSeconds` (60); the file
is restricted to the server's own user where the file system allows it and is deleted after 7 days. Your arcade
milestones are yes/no flags in Neroland Core's progression store; Core exports and erases them.

**Erasure.** NeroArcade is registered with Neroland Core's shared per-player erasure hook, so one request removes
you from every Neroland mod: `/neroland data eraseme`, an operator's `/neroland data erase <uuid>`, or Core's
inactivity sweep if the server owner enabled one. For NeroArcade it:

- deletes your leaderboard rows and personal bests on every board, and your credit setting;
- **unpublishes** every Exchange listing you published (the row is deleted) and deletes your drafts;
- removes every Exchange listing of a game you authored, whoever listed it; in any draft another player still holds
  of your game, replaces you as author with an anonymous "erased" marker that can never be named, credited or paid,
  and clears its credit;
- unseats you, and forgets your rate limits, your hearts this session, your export cooldown and your companion-app
  events, request ids and notification windows (Neroland Core removes your stored alerts);
- tombstones you as the draft origin on every **loaded** Game Bench (see the table above);
- deletes your export file;
- then refreshes each store's backup copy, so nothing can come back from it.

Cartridges already in inventories, chests, cabinets, duplicators and bench trays, and the draft origin of a bench in
an unloaded chunk, are outside the server's reach (see [the inventory-item limit](#the-inventory-item-limit)).

## Switching features off

Server owners can switch whole features off in `config/neroarcade.properties` (all enforced by the server):

| Switch | Effect when `false` |
|---|---|
| `exchange.enabled` | the kiosk refuses every action; listings are left untouched |
| `leaderboards.enabled` | finished matches write no rows or personal bests; existing boards are kept and shown |
| `multiplayer.enabled` | Link Buses join nothing; every cabinet plays on its own |
| `pushNotifications.enabled` | NeroArcade publishes nothing to the NeroLink bridge (in-app events still update when the app reads them); the stored alert still exists and a NeroLink bridge may announce it with a generic "alerts update" notification that names nothing — a NeroLink release that honours `_push` suppresses that too |
| `economy.coinOp` (default `false`) | seats and downloads are free; NeroEconomy is never called |
| `link.enabled` | NeroArcade does not register with the companion app at all |

## The NeroLink companion app

If the server runs the NeroLink bridge and you pair the NeroLink app, NeroArcade answers the app with data about
**you only**, computed when the app asks:

- **Library:** your drafts, the games you published (title, genre words, revision, plays, hearts), the boards where
  you hold a personal best (your score; your rank and time only if you hold a top row), your credit setting and how
  many drafts you have. The server cannot see inventories, so a cartridge in a chest is not listed.
- **Leaderboard of one game:** ranks, scores and times; a name only for players who turned credit on (otherwise
  "Anonymous"); your own row and best. Never a UUID. Asking about a game that does not exist looks exactly like an
  empty board.
- **Cabinets:** only the cabinet you are sitting at, labelled by dimension and hall size ("Overworld · Linked hall
  (3 cabinets)"). Never a coordinate, never anyone else's seat.
- **Events:** your last 20 arcade events, in memory only, cleared on restart and on erasure.
- **Wiki:** the public wiki pages (the same for everyone).

The app can **export** a published game (public data; another author is replaced by an anonymous marker and named
only if the listing already shows their credit) or your own draft, and **import** a game file into your own drafts
(you become its author, credit is cleared, the server's chat filter checks the title, at most 10 an hour). Nothing
is ever put into another player's drafts or inventory.

**Phone notifications go through third-party processors.** A notification leaves the game server through the
NeroLink relay (`nerorelay.neroserver.xyz`, a service operated for the Neroland community) and is delivered by Google
Firebase Cloud Messaging (Android) or Apple Push Notification service (iOS). The relay and these services are **third-party processors**:
they see the notification text, its category (`neroarcade`), your app's push token and the server's relay id, for as
long as delivery takes. Notifications are off until you turn the **Arcade** category on in the app, and contain only:

- *Record beaten*: the game's title and your new rank. **Never who beat your record and never where.** At most one
  per game per hour (`link.push.recordCooldownMinutes`).
- *Cartridge downloaded*: the game's title and how many times it was downloaded today (a count per listing, never
  who). Once a day (`link.digestHour`), at most five.

Your own match results appear in the app's event list but never as a notification. The relay keeps a delivery
cooldown of about a minute per player and category and does not store notification text; its own retention is not
under NeroArcade's control.

## Crash reports (telemetry)

NeroArcade sends opt-out, anonymous crash reports through [Sentry](https://sentry.io) (EU servers). Reporting is
**on by default**; a report contains only:

- the stack trace of an error that involves NeroArcade code (errors from other mods or Minecraft are dropped), and
  for an error NeroArcade caught and recovered from, a short fixed label of where it happened (never a player name,
  UUID, position or text a player typed);
- the NeroArcade, Minecraft, loader and Java versions, the operating system, and whether it is a client or a server;
- **the list of other loaded mods (their public ids and versions) and how many there are**, so a crash caused by a
  mod conflict can be found;
- a random event id.

Never your IP address (`sendDefaultPii` is off), username, UUID, machine name, world data or chat. Your account name
and home folder are replaced with `[user]`/`~` and anything shaped like a UUID with a placeholder before sending;
absolute file paths are removed. At most 10 reports per session. NeroArcade's own logs carry no player names or
UUIDs at info, warn or error level. To opt out, set `telemetryEnabled=false` in `config/neroarcade.properties`
(a per-computer setting a server cannot override; takes effect on restart).

## Contact and your rights

A server's operator is responsible for the data on their server; the commands above are how you exercise your rights
of access, objection (credit) and erasure there. For any data-subject request about NeroArcade, or any question
about this page, contact **[info@neroland.co.za](mailto:info@neroland.co.za)**; for a crash report, include the
approximate date and time so the matching report can be found and removed. Sentry acts as a processor for crash
reports ([privacy policy](https://sentry.io/privacy/), [DPA](https://sentry.io/legal/dpa/)).

You may also complain to a data-protection authority: in South Africa the Information Regulator
([inforegulator.org.za](https://inforegulator.org.za)), elsewhere the authority where you live (for the EU, your
member state's authority).

## Changes to this policy

If a future version of NeroArcade stores any further player-attributable data, this page will be updated **before**
that version is released, the change will be called out in the changelog, and that data will join NeroArcade's
erasure hook so a single erasure request removes it alongside every other Neroland mod. If a future version collects
more telemetry, this page will be updated first and `telemetryEnabled=false` will keep covering all of it.
