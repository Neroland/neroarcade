# NeroArcade

> Part of the Neroland sci-fi Minecraft mod family, built on **Neroland Core**.

**Status:** `0.1.0-beta.1` — feature-complete; runtime verification in a live game is pending. Player
documentation is in the [wiki](wiki/Home.md); scope cuts and known gaps are in the [changelog](CHANGELOG.md).

NeroArcade is a mini-game construction kit. Players build a game from **module chips** at a **Game Bench**, save it
to a **Cartridge**, and play it on an **Arcade Cabinet** whose screen everyone nearby can watch. Cartridges are data,
not code: they can be copied, published, downloaded and remixed. Cabinets joined by a **Link Bus** play versus and
co-op on one server-side simulation.

## What it does

- **Module contract** (`module`, `module.hook`, `module.pack`). Ten fixed slots — ARENA, PAWN, RULE, HAZARD,
  PICKUP, CONTROL, PACE, MODIFIER, MULTIPLAYER, THEME — one chip each, up to three in HAZARD and PICKUP. A chip is a
  Java class with typed settings (whole numbers, switches, choices) and pure hooks; 43 chips ship, seven of them the
  empty-slot defaults. A new chip never needs a change to the contract or the runtime. See
  [Modules](wiki/Modules.md).
- **Game definitions** (`game`). `GameDef` is a cartridge: id (16 base32 characters), title, slots and settings,
  lineage, revision, author id and opt-in credit. One fail-closed `CartridgeValidator` (8 KiB NBT, 12 KiB JSON on
  import; unknown chips refused, never dropped) runs on every path that accepts a cartridge.
- **Deterministic runtime** (`runtime`, `runtime.net`). A fixed 20 Hz step, integer-only state, entities sorted by
  id, a SplitMix64 stream per match, a fixed hook order and an FNV-1a state hash. A match is reproducible from
  `(GameDef, seed, inputLog)`; the test suite replays every example cartridge to a pinned hash on every cell.
  Seated players and spectators receive keyframe/delta snapshots (10 Hz, a keyframe every 2 s, capped in bytes);
  clients interpolate and predict only their own pawn.
- **Blocks and items** (`block`, `item`, `cabinet`, `bench`, `duplicator`, `exchange`, `bus`). Arcade Cabinet
  (1 × 2), Game Bench, Cartridge Duplicator, Arcade Exchange, Link Bus, Link Cable and Marquee Screen; Cartridge,
  Blank Cartridge, Blank Chip and one Module Chip item for every chip. A solo cabinet is a bus of one; a bus of up
  to four cabinets runs one `MatchHost` on the cabinet holding the cartridge, with sticky host and seat assignment.
- **Leaderboards** (`board`). A top ten and personal bests per cartridge id, written only by `MatchHost.finish`,
  capped, expiring (90 days; personal bests 365) and naming a player only with consent.
- **Sharing** (`duplicator`, `exchange`). Copy at the Duplicator; publish, unpublish, download, heart and fetch
  drafts at the Exchange (200 listings, 16 per author, no eviction). A remix is a bench save of someone else's game:
  new id, the original in the lineage.
- **Gallery** (`command`, `client.GalleryCapture`). `/neroarcade gallery [clear]` builds and removes a showcase hall
  of every chip running recorded demos; `/nsarcgallery capture` and `/nsarcber` are client tools for screenshots and
  the renderer budget. See [Gallery](wiki/Gallery.md).
- **Client** (`client.*`). 64 × 64 indexed screens drawn from palette-swapped sprite sheets (one shared pool of 24
  textures), culled block-entity renderers, the seated HUD, the bench, duplicator and Exchange screens, and the
  arcade key bindings.
- **Privacy** (`privacy`, `data`). Opt-in credit, `/neroarcade privacy export`, erasure through Neroland Core's
  shared hook across every store (with backup refresh), retention sweeps and kill switches. See
  [PRIVACY.md](PRIVACY.md).
- **NeroLink module** (`link`). Library, leaderboard, cabinets, events and wiki sections; `export_cartridge` and
  `import_cartridge` actions; "Record beaten" and download-digest notifications. See
  [USING-NEROLINK.md](USING-NEROLINK.md).
- **Progression and integrations** (`progression`, `compat`). Core milestones and threshold channels, an "Insert
  Coin" NeroQuests chapter as data, an optional NeroEconomy coin-op bridge, Core palette registration, and no-op
  seams for tournaments, soundtracks and colony morale.

## Requirements

- **Minecraft** 26.1.2, 26.2 or 26.3 on **NeoForge**, **MinecraftForge** or **Fabric**.
- **Neroland Core 1.15.0** or newer (below 2.0). Every loader manifest floors it at the compiled version.
- **Java 25.**

Optional, each detected at start-up; NeroArcade is complete without them:

| Mod | Minimum | What it adds |
| --- | --- | --- |
| NeroEconomy | 0.4.0-beta.1 | Coin-op seats and paid downloads (`economy.coinOp`, off by default). |
| NeroLink | 1.0.0 (1.1.0 for rich notification text) | The companion-app module: library, leaderboards, export/import, notifications. |
| NeroQuests | — | Loads the "Insert Coin" chapter shipped as data. |

## Supported cells

Nine cells, each a Stonecutter node `:<loader>:<mc>`:

| | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- |
| NeoForge | `:neoforge:26.1.2` | `:neoforge:26.2` | `:neoforge:26.3` |
| Forge | `:forge:26.1.2` | `:forge:26.2` | `:forge:26.3` |
| Fabric | `:fabric:26.1.2` | `:fabric:26.2` | `:fabric:26.3` |

## Layout

- `common/` — shared, loader-agnostic source spliced into every loader node; plain-JVM JUnit 5 tests in
  `common/src/test/` (with the example cartridges and pinned hashes in `common/src/test/resources/cartridges/`)
- `fabric/`, `forge/`, `neoforge/` — one branch per loader: entry points, network and platform implementations,
  manifest templates
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert
- `tools/` — `merge_lang.py` and the `lang/` fragments, asset generators and checks, `ecj.prefs`, the logo script
- `art/` — logo outputs and the store descriptions
- `wiki/` — the player wiki; published to the GitHub wiki and bundled into the jar for the companion app

[AGENTS.md](AGENTS.md) describes every package and the invariants a change must keep.

## Building

Build the per-node tasks. A plain `./gradlew build` is not the supported entry point (the root loader projects are
empty containers).

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
./gradlew :fabric:26.2:ecjCheck       # static analysis (Eclipse compiler, tools/ecj.prefs)
```

- Unit tests run in the three NeoForge nodes: `./gradlew :neoforge:26.2:test`.
- **Economy flag.** By default (`neroeconomy_compat=true` in `gradle.properties`) the coin-op bridge compiles
  against NeroEconomy. `-Pneroeconomy_compat=false` builds without the NeroEconomy artifact; the bridge, its service
  line and the optional manifest entry are left out. Both configurations must build. Dev clients load NeroEconomy
  only with `-PwithNeroEconomy`.
- Neroland Core resolves from `mavenLocal()` first, then GitHub Packages; see [USING-CORE.md](USING-CORE.md).
- Lang strings live in `tools/lang/*.en_us.json`; run `python tools/merge_lang.py` after editing one
  (`--check` verifies).
- Jars land in `<loader>/versions/<mc>/build/libs/`.

## Documentation

- [Wiki](wiki/Home.md) — playing, building games, sharing, multiplayer, commands, config, privacy
- [CHANGELOG.md](CHANGELOG.md) — releases, scope cuts, known gaps and placeholders
- [PRIVACY.md](PRIVACY.md) — the full privacy and telemetry disclosure
- [USING-CORE.md](USING-CORE.md) — how NeroArcade consumes Neroland Core
- [USING-NEROLINK.md](USING-NEROLINK.md) — the companion-app module: sections, actions, events
- [AGENTS.md](AGENTS.md) — layout, invariants and conventions for contributors

## License

All Rights Reserved; modpacks are allowed. See [LICENSE](LICENSE).
