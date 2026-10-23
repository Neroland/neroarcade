# NeroArcade privacy notice

NeroArcade is built to comply with POPIA (South Africa) and the GDPR (EU).

## What NeroArcade stores today

### Cartridges

A **cartridge** is an item that holds one game. When you save a game at a Game Bench, the cartridge records:

- the game itself (its chips, settings and title, which you type);
- your **player UUID**, always, as the cartridge's author. It is never shown anywhere: not in the tooltip, not
  in `/neroarcade cartridge inspect`, not on any screen. It exists so a future erasure request and the
  "re-save your own cartridge" rule can recognise your work;
- a **credit name** only if you opted in (see "Credit opt-in" below) when you saved. Otherwise the cartridge
  says "by Anonymous builder" and carries no name.

A cartridge is an item: it lives in inventories, chests and item frames like any other item, and copies of it
leave the server's control the moment someone else holds them. NeroArcade cannot reach cartridges in other
players' inventories, so an erasure request cannot rewrite them. Cartridges are tagged
`neroeconomy:untradeable` so economy mods do not list them for sale.

A placed cabinet keeps the inserted cartridge item (and with it the author UUID) in the world save, exactly as
a chest would. The cabinet's client sync sends only the title, the theme colour and whether the cartridge is
readable — never the author.

### Seats

When you sit at a cabinet (or test a game at a bench), the server remembers **which cabinet you sit at, in
memory only**. It is never written to disk, exported or logged, and it is forgotten when you leave the seat,
walk away, log out or the server stops.

### Leaderboards

When a match ends normally, the server keeps, for each cartridge (copies share one board):

- **top-ten rows**: your player UUID, your score, the time it was set, and your name **only if you had credit
  turned on at that moment** — otherwise the row says "Anonymous";
- **your personal best**: your player UUID and your best score on that cartridge.

Nothing else: not which cabinet you used, not who you played with, not every game you played. Abandoned matches
(someone broke the cabinet, everyone left) and hot-seat games record nothing; a player who walks out of a match
keeps their personal best but never gets a top-ten row.

**Retention:** top-ten rows are deleted **90 days** after they were set (a server may choose 1 to 365 days with
`privacy.leaderboardRetentionDays`; a year is the longest allowed). The server checks when the world loads and
every hour. Personal bests are kept until you ask for your data to be erased. A server owner can stop recording
altogether with `leaderboards.enabled=false`.

Other players see ranks, scores and opted-in names only — never a UUID.

### Credit opt-in

`/neroarcade privacy credit on` lets NeroArcade show your Minecraft name; `/neroarcade privacy credit off` takes
it back. **It is off by default.** While it is on, new leaderboard rows and games you save from then on carry
your name. Turning it off removes your name from every leaderboard row on the server straight away; turning it
on never adds your name to older rows. Cartridges you already gave to others keep whatever they carried — the
server cannot reach items in other players' inventories. The server stores only that you turned credit on.

### No play history

NeroArcade keeps no record of which games you played or when (`privacy.playHistory` is always false).

### Erasure

NeroArcade registers with Neroland Core's per-player data-erasure hook. A `/neroland data eraseme` request (or an
operator's erasure) removes your leaderboard rows, your personal bests and your credit setting from this world,
and refreshes the backup copies so they cannot come back.

## Crash reports

NeroArcade contains opt-out, anonymous crash reporting (Sentry, EU servers). In this build it is **inert**:
no reporting project is configured, so nothing is sent anywhere. When it is enabled in a future release it
will send only NeroArcade stack traces and mod, Minecraft, loader, OS and Java versions, never your IP,
username, UUID, world data or chat, and file paths will be scrubbed of your account name. Set
`telemetryEnabled=false` in `config/neroarcade.properties` to opt out.

## Sections to be completed as features land

- Leaderboards: data export
- The Arcade Exchange (published games)
- Companion-app (NeroLink) data
