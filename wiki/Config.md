# Config

NeroArcade's settings live in `config/neroarcade.properties`, created on first launch. Keys are flat (the dots are
part of the name). Operators can reload the file with `/neroland config reload`; a key that says "restart" takes
effect only after one. Numbers outside the range are brought back inside it.

**Server** keys are decided by the server and sent to every player when they join, so a client always runs the
same budgets as the server. **Client** keys belong to each player's own computer: the server never sets or reads
them.

## Client (this computer only)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `telemetryEnabled` | true | | Anonymous NeroArcade-only crash reports. Set `false` to opt out (restart). Inert in this version: nothing is sent. See [Privacy](Privacy.md). |
| `client.scanlines` | false | | Draws arcade screens with CRT scanlines. |
| `client.screenRenderRange` | 0 | 0–64 | Draws arcade screens only within this many blocks. 0 uses the server's `screenRenderRange`; a larger number than the server's is capped to it. |

## Cabinets and seats (server)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `screenRenderRange` | 32 | 8–64 | Blocks within which arcade screens are drawn, and within which a nearby player keeps a cabinet awake. |
| `seatRange` | 3.0 | 1.5–8.0 | How far a seated player may step from the cabinet before the seat is released. |

Cabinets and the Duplicator do not use power in this version, so there is no power setting.

## Runtime budgets (server)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `runtime.maxArenaSide` | 32 | 8–32 | Largest arena side in cells; a cartridge with a bigger arena is refused. |
| `runtime.maxEntities` | 256 | 32–256 | Most moving things in one match; extra spawns do nothing. |
| `runtime.maxInputPacketsPerSecond` | 30 | 10–60 | Input packets accepted per seat per second; the rest are dropped (nobody is kicked). |
| `runtime.snapshotMaxBytes` | 8192 | 6400–16384 | Largest screen update; a bigger one is sent as a full frame. |
| `runtime.snapshotHz` | 10 | 5–20 | Screen updates per second to a cabinet's viewers. |
| `runtime.keyframeTicks` | 40 | 20–200 | Ticks between full-frame screen updates. |
| `runtime.maxAwakeCabinets` | 32 | 4–128 | Cabinets that may run a game at once; further ones queue ("ARCADE BUSY"). |
| `runtime.sleepAfterTicks` | 100 | 40–1200 | Ticks a cabinet with nobody seated or near waits before it sleeps. |
| `runtime.maxMatchSeconds` | 600 | 30–1800 | Longest match; a match still running ends in a draw. |

## Switches (server)

| Key | Default | What it does when `false` |
| --- | --- | --- |
| `multiplayer.enabled` | true | Link Buses join nothing; every cabinet plays on its own. |
| `leaderboards.enabled` | true | Finished matches write no rows or personal bests; existing boards stay and are shown. |
| `exchange.enabled` | true | The Arcade Exchange refuses every action (listings stay); the app's export and import are off too. |
| `link.enabled` | true | NeroArcade does not register with the NeroLink companion app (restart). |
| `link.actions.export_cartridge` | true | The app cannot export a cartridge. |
| `link.actions.import_cartridge` | true | The app cannot import a cartridge into drafts. |
| `pushNotifications.enabled` | true | NeroArcade sends nothing to the NeroLink bridge, so no phone notifications; the app's events list still updates. |

## Exchange and economy (server)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `exchange.pageSize` | 8 | 4–16 | Listings per Exchange page. |
| `economy.coinOp` | false | | With NeroEconomy installed, taking a cabinet seat costs `economy.coinOpPrice`. A refused payment gives the seat back. If NeroEconomy is installed but its economy is switched off, every seat is refused. |
| `economy.coinOpPrice` | 10 | 0–10000 | Price of one seat. |
| `economy.downloadPrice` | 0 | 0–10000 | Price of one Exchange download, paid to the game's builder (0 = free). |

## Privacy and limits (server)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `privacy.leaderboardRetentionDays` | 90 | 1–365 | Days a top-ten row is kept. **Hard cap 365.** |
| `privacy.personalBestRetentionDays` | 365 | 1–365 | Days a personal best is kept after it was set; beating it restarts the clock. **Hard cap 365.** |
| `privacy.leaderboardMaxEntries` | 10 | 1–25 | Rows on each board. |
| `privacy.exchangeMaxListings` | 200 | 1–1000 | Most listings on the Exchange; a full Exchange refuses, never deletes. |
| `privacy.listingsPerAuthor` | 16 | 1–200 | Most listings one player may have (operators exempt). |
| `privacy.draftsPerPlayer` | 8 | 1–32 | Imported drafts kept per player. |
| `privacy.exportCooldownSeconds` | 60 | 10–3600 | Seconds between two `/neroarcade privacy export` requests. |
| `privacy.playHistory` | false | | Always false: NeroArcade keeps no record of which games a player played. Declared for the privacy notice. |

## Companion app (server)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `link.importPerHour` | 10 | 1–100 | Imports one player may make from the app per hour (and at most 3 a minute). |
| `link.push.recordCooldownMinutes` | 60 | 5–1440 | Minutes between two "Record beaten" notifications for the same player and game. |
| `link.digestHour` | 18 | 0–23 | Server-local hour of the daily "Cartridge downloaded" notification. |
| `link.libraryPageSize` | 50 | 10–50 | Most games in one page of the app's library. |
