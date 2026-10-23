# NeroArcade — agent guide

NeroArcade is a Minecraft mod for building, playing and sharing mini games inside Minecraft. It is part of
the Neroland mod family and is built on **Neroland Core**.

**Status: Stage 2 — core loop playable solo (needs in-game verification) (0.0.1-alpha.1).**

## Repository layout

| Path | What lives there |
|------|------------------|
| `common/src/main/java/za/co/neroland/neroarcade/` | Loader-agnostic code (vanilla + Core APIs only, no loader imports) |
| `common/src/main/resources/` | Shared assets, lang, mixin config, `pack.mcmeta`, mod-list logo |
| `common/src/test/java/` | Plain-JVM JUnit 5 tests (wired into the NeoForge cells) |
| `fabric/`, `forge/`, `neoforge/` | One branch per loader: entry points, network + platform implementations, manifest templates |
| `tools/` | `ecj.prefs`, `merge_lang.py` + `lang/` fragments, `gen_logo.py`, the Gradle MCP server |
| `art/` | Logo outputs and the Modrinth / CurseForge descriptions |
| `wiki/` | Player-facing wiki pages, published to the public GitHub wiki |

Common packages so far: `platform` (the `PlatformInfo` / `NetworkPlatform` / `Services` trio), `config`,
`telemetry`, `data` (erasure registration), `privacy` (`BackupHook`), `menu` (`MenuOpener`), `server`
(`ServerHooks` lifecycle seam, `ArcadeServer` seat cleanup), `command` (`/neroarcade`), and:

| Package | What lives there |
|---------|------------------|
| `registry` | `ArcadeIds` (every registry path as a plain string), `ModDataComponents`, `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModMenus`, `ModSounds`, `ModCreativeTab` |
| `block` | The seven blocks, `ArcadeBlockProperties` (every blockstate property; `AssetContractTest` checks it against the JSONs), `TwoHighBlock` (1x2), `CabinetStatus`, `PadDirection` |
| `item` | `StoredCartridge` (the `neroarcade:game_def` value: canonical NBT, read through the validator), `CartridgeItem`, `ModuleChipItem` + `ModuleChips`, `CartridgeTint`, `ArcadeBlockItem` |
| `cabinet` | `ArcadeCabinetBlockEntity`, `MatchHost` (phase machine, the one board-write path), `Seat`, `SeatRegistry`, `InputGate`, `AwakeCabinets`, `SleepClock`, `HostEnv`/`LevelHostEnv`, `SeatHolder`, `SeatPackets`, `CabinetInteraction`, `BoardWriter`, `BusMatchResult` |
| `bench` | `GameBenchBlockEntity`, `GameBenchMenu`, `BenchDraft` (pure), `BenchActions`, `BenchSlots`, `BenchLayout`, `TitleRules`, `CreditResolver` |
| `network` | `ArcadeNetwork` (seven payloads), the payload records, `ArcadeClientSink` (where client-bound payloads land) |
| `client` | Client only (never loaded on a dedicated server): `ArcadeClient` (sink, tick, Esc = leave, sounds), `ArcadeColours`, `ArcadeClientConfig`; `client.screen` (`ScreenRenderer` 64x64 indexed screens, `ThemePalettes`, `SpriteSheets`, `ArcadeFont`, `ArenaLayout`, `ScreenPixels`, `GameBenchScreen`); `client.render` (`ArcadeCabinetRenderer` BER, `CabinetScreens`, `ScreenTexture`, `TextureLru`, `ScreenAssets`); `client.match` (`ClientMatches`, `ClientMatch`, `LocalPrediction`, `Interpolation`); `client.input` (`ArcadeKeys`, `InputSender`, `SeatedInput`); `client.hud` (`ArcadeHud`); `client.bench` (`ClientBenchState`, `BenchModel`, `BenchPreview`); `client.widget` |

The game core:

| Package | What lives there |
|---------|------------------|
| `module` | The module contract: `ModuleType` (ten slots), `ArcadeModule`, properties, `ModuleRegistry`, defaults, bench warnings |
| `module.hook` | The ten hook interfaces (`ArenaModule` ... `ThemeModule`), `ModuleContext`, `Intent` |
| `module.pack` | The shipped chips; `ArcadeModules.init()` registers them in init step 3. One sub-package per slot |
| `game` | `GameDef` (a cartridge), its codecs and schema, `CartridgeIds`, `CartridgeValidator`, `ResolvedGame` |
| `runtime` | The deterministic simulation: `MatchState`, `ArcadeRuntime`, `Rng`, `InputLog`, `Replay`, `HeadlessMatch`, `Budgets` |
| `runtime.net` | Snapshot keyframe/delta codec and the snapshot / seat-input payload records |

A new chip is a class in `module.pack.<slot>` registered from `ArcadeModules`; it never needs a change to
`module`, `module.hook` or `runtime`.

## Build

Stonecutter produces nine cells (three loaders x three Minecraft lines). Never run a plain `build`: the root
`:fabric`, `:forge` and `:neoforge` projects are vestigial containers. Build the cells explicitly:

```text
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build
./gradlew :fabric:26.2:ecjCheck
```

The unit tests run as part of every NeoForge cell build. `ecjCheck` runs the Eclipse analyzer with
`tools/ecj.prefs` (the same settings VS Code uses) and fails only on errors.

Lang strings live in `tools/lang/*.en_us.json`. Edit a fragment, then run `python tools/merge_lang.py`;
never edit `en_us.json` by hand (`LangMergeTest` enforces this).

## Runtime invariants

The simulation in `module`, `game` and `runtime` must give the same result on every loader, Minecraft line
and JVM. Desync is a defect, and `ReplayTest` pins final state hashes per example cartridge
(`common/src/test/resources/cartridges/hashes.txt`).

- **Integer-only.** No `float` or `double` anywhere in `module`, `game` or `runtime` (`NoFloatsTest` checks
  fields, parameters and return types by reflection). Positions are fixed point, 256 units per cell; use
  shifts, not division, to get a cell.
- **Pure hooks.** A hook reads its `ModuleContext`, changes the match only through `MatchState`'s API and draws
  randomness only from `ctx.rng()` (SplitMix64 over `MatchState.rngState`). Never a static, a clock, a thread,
  a `Level`, a `Player`, `Math.random` or `java.util.Random` (`HookPurityTest`). No chip compares another
  chip's id; capability questions go through `ModuleFlag`s.
- **Sorted entities.** Entities live in one `int[]` sorted by id and are visited in ascending id order. No
  `HashMap`/`HashSet` is ever iterated to produce state.
- **Fixed order.** `ArcadeRuntime.step` calls the hooks in the contract's order (pace, arena tick, runtime
  opponent, control, multiplayer mask, modifier, pawn input then pawn tick, hazards 0-2, pickups 0-2,
  collisions, modifier, multiplayer, housekeeping, rule, resolve). The collision pass is: per seat (pickups,
  hazards, enemy projectiles), directional pawn-vs-pawn, goal cells (8b), projectiles vs hazards (8c), all in
  entity-id order. `TickOrderTest` pins it.
- **The pawn decides hits, the runtime applies them.** `PawnModule.onHit` returns a `HitOutcome`
  (DAMAGE / DEFLECT / IGNORE); a hook never calls `damageSeat` for the hit itself. A HAZARD or another seat's
  PROJECTILE on a `Cell.GOAL_<seat>` cell costs that seat a life. The runtime opponent's input comes from
  `PawnModule.opponentInput` (default `RuntimeOpponent.greedy`) every `OPPONENT_CADENCE` simulated ticks; it
  is an input, never logged, and re-derived by a replay. A pawn that keeps the default gets the greedy split:
  `RuntimeOpponent.target` is re-chosen every `OPPONENT_CADENCE` ticks and `RuntimeOpponent.steer` runs every
  simulated tick (both pure; the target is a runtime latch like the held input), so it can stop on any cell.
- **Cells are append-only bytes** (`runtime.Cell`): EMPTY 0, WALL 1, VOID 2, EXIT 3, EXIT_LOCKED 4, LANE 5,
  GOAL_0..3 6-9, BLOCK 10. `Cell.BLOCK` is a settled stack cell (the stacker writes and clears it, arenas never
  do): solid and non-lethal, `Cell.blocks` is true, so it stops pawns, hazards and the opponent exactly like WALL
  but stays distinguishable for row clears, the renderer and the hash. A new cell value is appended, never
  renumbered.
- **Hash.** `MatchState.stateHash()` is FNV-1a 64 over the keyframe bytes. Changing the keyframe layout
  changes every pinned hash; do it on purpose or not at all.
- **Cartridges are data and fail closed.** `CartridgeValidator` refuses (never clamps, truncates or
  substitutes) and an unknown module id is an error. Modules are Java, registered once at init; the
  registry is frozen afterwards.
- **No player in the simulation.** `MatchState` holds seat indexes only; the seat-to-player map lives in the
  cabinet, so replays hold no personal data.
- **Every chip speaks plainly** (module contract, `ArcadeModule` / `RuleModule` default methods). Each chip ships
  `module.neroarcade.<slot>.<name>.effect` — the rack row's short effect, at most 22 characters rendered with
  default values — and overrides `Object[] effectArgs(PropertyValues props)` (pure; `Integer`, `String` or a
  translatable `Component` for a word that is itself a lang key) when the line has live values. Every RULE also
  ships `.hud` (at most 24 characters, the seated HUD's amber line) and `int[] hudArgs(PropertyValues props)`.
  The long `.desc` stays on hovers. No chip ships a property without a visible effect in the preview. CONTROL
  `legend(props)` lines add only what the keycap line does not already say; on the client each is translated
  with `%1$s` = the bound A key name and `%2$s` = the bound B key name. `ShippedModulesConventionTest` and
  `BenchScreenModelTest` hold the keys and lengths.

## Cabinet and bench invariants

- **A solo cabinet is a bus of one** (`busId 0`, seat 0). `MatchHost` is the only host; Stage 4 adds joining,
  never a second path. `MatchHost` takes a `HostEnv`, so it runs in a plain JVM test.
- **One write path.** `MatchHost.finish` is the only caller of `BoardWriter.record`, once per match, never for
  an `ABANDONED` result. Stage 2 wires `BoardWriter.NONE`.
- **Seats are runtime only.** `Seat`, `SeatRegistry` and `AwakeCabinets` are memory; nothing about a seat is
  saved, exported or logged. One seat per player server-wide.
- **Input acceptance** (`InputGate`): the sender is the connection's player; the seat's UUID must match; the
  match id must match; 30 packets per second per seat. Refusals are silent and counted; nothing ever kicks.
- **Teardown** with world access lives in `preRemoveSideEffects` (real removal only). `setRemoved` also runs on
  chunk unload and stays world-inert (memory bookkeeping only).
- **Cartridge component is lossless.** `neroarcade:game_def` stores the cartridge's NBT; every read goes
  through `CartridgeValidator`, so a newer or unknown cartridge is "UNREADABLE HERE", never dropped.
- **Chips are templates.** The bench never consumes a chip; LOAD materialises "ghost" chips that cannot be
  taken out and are never dropped.
- **A cartridge id is never shared.** SAVE rewrites in place (same id, revision + 1) only when the tray holds the
  draft's own cartridge **and** the draft's recorded author is present and is the saver; every other save goes
  onto a Blank with a new id from `CartridgeIds.mint`, the origin appended to the lineage (capped at 8, root
  kept). An authorless origin is never "own"; someone else's or a different game's cartridge in the tray is
  refused with a sentence. `BenchDraft.planSave` → `SavePlan` is pure; `SavePlanTest` pins it.
- **Esc leaves without trusting the client.** Closing the bench menu stops that player's test play on the server
  (`GameBenchMenu.removed` → `onMenuClosed`); a seated player always sees "ESC leave" on the HUD.
- **A loaded cabinet looks alive.** The strip is `OFF` only with no cartridge; a readable cartridge with the host
  asleep is `READY`, and the client draws the attract loop locally from the synced `GameDef` — sleep governs
  `MatchHost` simulation, not the title card.
- **Client-bound payloads land in `ArcadeClientSink`.** The client module installs a sink; common code never
  touches client classes.
- **Client invariants.** Screen textures load standalone from `textures/screen/**`, never through the block atlas;
  each theme has the base sheets (`pawns` 80, `hazards` 80, `pickups` 32, `tiles` 48 px wide) plus extension
  sheets (`pawns_ext` 16: stacker = pawn pair 5; `tiles_ext` 16: `TILE_BLOCK` 6 drawn for `Cell.BLOCK`,
  `TILE_GOAL` 7 drawn for `Cell.GOAL_0..3` with palette 5 remapped to the goal seat's colour), addressed as if
  the base sheet continued. A base sheet's width never changes; new roles go in an ext sheet. A wrongly sized
  sheet is refused loudly (`SpriteSheets.fromArgb` throws; the loader logs and leaves it transparent). A SEGMENT
  draws frame 1 of its owning pawn's pair;
  at most `TextureLru.DEFAULT_CAP` (24) cabinet screens hold a dynamic texture; the cabinet BER culls by
  `screenRenderRange` and facing, never blanket `true`. The client never decides a score or a phase: it draws the
  snapshot, predicts only the seated player's own pawn (≤ 3 ticks, snap beyond one cell) and sends input on change
  plus a 5 Hz keep-alive (≤ 20/s). `client.*` config keys are this computer's only, never server-authoritative.

## Conventions

- **Core floor is derived, never hardcoded.** `nerolandcore_version_range: "[${nerolandcoreVersion},2.0)"` in
  the NeoForge and Forge builds, `">=${nerolandcore_version} <2.0.0"` in `fabric.mod.json`. Pin
  `nerolandcore_version` to a tagged Core release.
- **Every `SavedData` goes through Core's `SavedDataRecovery`.** A corrupt `.dat` must never become a
  repeating crash. Every player-keyed store registers with `data/NeroArcadeData` and refreshes its backup
  after erasure.
- **Player data routes through Core's `PlayerDataErasure`** (POPIA/GDPR): minimise it, set retention limits,
  support export, erasure and opt-out.
- **Own network channel `neroarcade:main`.** Payloads are declared once in `network/ArcadeNetwork`; never add
  to Core's network registry.
- **Platform trio.** Loader facts come from `Services.PLATFORM` / `Services.NETWORK`, resolved during init and
  never lazily on a tick path.
- **Numbered init.** `NeroArcadeCommon.init()` runs fixed, numbered steps; new work fills the reserved slot.
- **`MenuOpener` for every GUI.** Never call `Player#openMenu` directly.
- **Config through Core's `ConfigSchema`** (`config/neroarcade.properties`).
- **Opt-out Sentry telemetry** with no personal data. The DSN is a placeholder until NeroArcade has its own
  Sentry project; keep the placeholder guard.
- **`publish.yml` rules.** Direct-curl CurseForge upload with `max-parallel: 1` and the 5xx retry loop, plus
  the Modrinth v3 environment-metadata step after `mc-publish`.
- **Own creative tab** (`registry/ModCreativeTab`, id `neroarcade`, title `itemGroup.neroarcade`). Never add
  items to Core's tab.
- **Never commit, tag or push.** Leave version control to the owner.
- **The wiki is public.** Everything under `wiki/` is published as is; keep private planning material out of
  it and out of the repository.
- **No commercial game names.** Mini games, templates, items and docs use original names only.
