# NeroArcade privacy notice

NeroArcade is built to comply with POPIA (South Africa) and the GDPR (EU).

## What NeroArcade stores today

### Cartridges

A **cartridge** is an item that holds one game. When you save a game at a Game Bench, the cartridge records:

- the game itself (its chips, settings and title, which you type);
- your **player UUID**, always, as the cartridge's author. It is never shown anywhere: not in the tooltip, not
  in `/neroarcade cartridge inspect`, not on any screen. It exists so a future erasure request and the
  "re-save your own cartridge" rule can recognise your work;
- a **credit name** only if you opt in. The opt-in is not built yet, so every cartridge saved today says
  "by Anonymous builder" and carries no name.

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

### No leaderboards or play history yet

This build keeps **no scores, no leaderboards and no play history**. A finished match is shown on the screen
and forgotten. Leaderboards arrive in a later release with their own retention limit, opt-in names, export and
erasure, and this notice will describe them before they ship.

NeroArcade already registers with Neroland Core's per-player data-erasure hook, so a `/neroland data eraseme`
request reaches NeroArcade from the first build that stores player-keyed data.

## Crash reports

NeroArcade contains opt-out, anonymous crash reporting (Sentry, EU servers). In this build it is **inert**:
no reporting project is configured, so nothing is sent anywhere. When it is enabled in a future release it
will send only NeroArcade stack traces and mod, Minecraft, loader, OS and Java versions, never your IP,
username, UUID, world data or chat, and file paths will be scrubbed of your account name. Set
`telemetryEnabled=false` in `config/neroarcade.properties` to opt out.

## Sections to be completed as features land

- Leaderboards: what is kept, for how long (retention), export and erasure
- Credit opt-in (`/neroarcade privacy credit on|off`)
- The Arcade Exchange (published games)
- Companion-app (NeroLink) data
