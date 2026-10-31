# NeroArcade — agent guide

NeroArcade is a Minecraft mod for building, playing and sharing mini games inside Minecraft. It is part of
the Neroland mod family and is built on **Neroland Core**.

**Status: 0.1.0-beta.1 — final review fixes applied; runtime verification pending.**

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
`telemetry`, `data` (erasure registration), `privacy` (`BackupHook`, `StoreAccess`, `CreditOptIn` + `CreditTable`, `Tombstones`, `ArcadePrivacy` (the `exports` eraser, export command work, export sweep), `PrivacyExport`, `ExportCooldown`), `menu` (`MenuOpener`), `server`
(`ServerHooks` lifecycle seam, `ArcadeServer` seat cleanup), `command` (`/neroarcade`; the creative gallery:
`GalleryLayout` pure floor plan, `GalleryCommands` build/clear, `GalleryStore` what each build changed), and:

| Package | What lives there |
|---------|------------------|
| `registry` | `ArcadeIds` (every registry path as a plain string), `ModDataComponents`, `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModMenus`, `ModSounds`, `ModCreativeTab` |
| `block` | The seven blocks, `ArcadeBlockProperties` (every blockstate property; `AssetContractTest` checks it against the JSONs), `TwoHighBlock` (1x2), `CabinetStatus`, `PadDirection` |
| `item` | `StoredCartridge` (the `neroarcade:game_def` value: canonical NBT, read through the validator), `CartridgeItem`, `ModuleChipItem` + `ModuleChips`, `CartridgeTint`, `ArcadeBlockItem` |
| `cabinet` | `ArcadeCabinetBlockEntity` (solo / bus host / bus node, `BusRole`), `MatchHost` (phase machine, lobby + ready, packed runtime seats, the one board-write path), `HotSeat`, `Seat`, `SeatRegistry`, `InputGate`, `AwakeCabinets`, `SleepClock`, `HostEnv`/`LevelHostEnv`, `SeatHolder`, `SeatPackets`, `CabinetInteraction`, `BoardWriter`, `BusMatchResult` |
| `bus` | Link Bus: `BusTopology` (pure flood fill: 16 cables, 4 cabinets, marquees), `BusElection` (pure host election), `BusAssignment` (pure sticky host + seat indexes), `BusRegistry` (runtime, per dimension: claims, dirty buses), `LinkBusBlockEntity` (discovery, roles, cable pulse, board pages), `MarqueeScreenBlockEntity`, `LevelBusWorld` |
| `exchange` | The Arcade Exchange: `KioskBoard` (the kiosk screen's rows: titles + hearts only), `ExchangeTable` (pure listings + drafts rules + NBT), `ExchangeStore` (SavedData via `SavedDataRecovery`, `"neroarcade:exchange"`; the Stage 7 read/draft API), `Listing`, `Draft`, `ExchangeHearts` (memory-only dedup + 20/h), `ExchangeRules` (author-or-op, title + chat filter), `ExchangePages` (page builder), `GenreTags`, `Exchange` (server ops, erasers, opt-out strip, hourly drafts sweep, play count), `ExchangeActions` (C2S handler, 4/s), `ExchangeMenu`, `ArcadeExchangeBlockEntity` |
| `duplicator` | `DuplicatorRules` (pure), `CartridgeDuplicatorBlockEntity` (60-tick copy, `copying` state), `DuplicatorMenu` (`clickMenuButton` 0 = COPY) |
| `progression` | `ArcadeMilestones` (threshold channels + player gates behind a `Sink`), `ArcadeMatchEnds` (the cabinet's `MatchListener`), `QuestHooks` (`neroarcade:hook/blank_cartridges`) |
| `compat` | `Seams` (resolved once at init step 5), `ModPresence`; `economy` (`CoinOpSeam`, `CoinOp` façade, `bridge/NeroEconomyCoinOp` — the only NeroEconomy import), `tournament` (`TournamentSeam`, `Tournaments`), `soundtrack`, `palette` (Core `PaletteRegistry`), `colonies` (`MoraleSeam`) |
| `board` | Leaderboards: `LeaderboardTable` (pure rules + NBT), `LeaderboardStore` (SavedData via `SavedDataRecovery`; package-private `record`), `Boards` (the `BoardWriter`, retention sweep, erasure registration, page builder), `RecordOutcome` |
| `link` | The NeroLink module (007, see `USING-NEROLINK.md`): `NeroArcadeLinkModule` (registered last, guarded; `"link"` eraser), `ArcadeSnapshotProvider` (`library`, `leaderboard`, `cabinets`, `events`, `wiki`), `ArcadeActionHandler` (`export_cartridge`, `import_cartridge`), `ArcadeEvents` (`RECORD_BEATEN`, `MATCH_RESULT`, `CARTRIDGE_DOWNLOADED`; `RecordCoalescer`), `ArcadePushCopy` (the only notification/alert/label text), `BoardKeys` (cartridge → board key), `ArcadeLinkContext`/`LiveArcadeLinkContext` (the seam tests fake), `LinkRequestDedup`, `LinkRateLimiter`, `WikiLibrary`, `ArcadeLinkInfo` |
| `bench` | `GameBenchBlockEntity`, `GameBenchMenu`, `BenchDraft` (pure), `BenchActions`, `BenchSlots`, `BenchLayout`, `TitleRules`, `CreditResolver` |
| `network` | `ArcadeNetwork` (ten payloads), the payload records, `ArcadeClientSink` (where client-bound payloads land) |
| `client` | Client only (never loaded on a dedicated server): `ArcadeClient` (sink, tick, Esc = leave, sounds), `ArcadeColours`, `ArcadeClientConfig`; `client.screen` (`ScreenRenderer` 64x64 indexed screens, `ThemePalettes`, `SpriteSheets`, `ArcadeFont`, `ArenaLayout`, `ScreenPixels`, `GameBenchScreen`); `client.render` (BERs `ArcadeCabinetRenderer` (screen + marquee title), `MarqueeScreenRenderer`, `GameBenchRenderer` (holo-rack), `DuplicatorRenderer` (platter + bar, `CopyClock`), `ExchangeRenderer` (listing screen); `CabinetScreens` (the one screen pool: cabinets, marquees, bench preview, kiosk), `AttractReplays`, `ScreenTexture`, `TextureLru`, `ScreenAssets`, `ArcadeQuads`, `RenderPlan` (ranges + vertex counts), `ArcadeBerStats`); `client.GalleryCapture` (`/nsarcgallery capture`, `/nsarcber`); `client.screen.AttractPlayer`; `client.match` (`ClientMatches`, `ClientMatch`, `LocalPrediction`, `Interpolation`); `client.input` (`ArcadeKeys`, `InputSender`, `SeatedInput`); `client.hud` (`ArcadeHud`); `client.bench` (`ClientBenchState`, `BenchModel`, `BenchPreview`); `client.exchange` (`ClientExchange`); `client.screen.ExchangeScreen`, `DuplicatorScreen`; `client.widget` |

The game core:

| Package | What lives there |
|---------|------------------|
| `module` | The module contract: `ModuleType` (ten slots), `ArcadeModule`, properties, `ModuleRegistry`, defaults, bench warnings |
| `module.hook` | The ten hook interfaces (`ArenaModule` ... `ThemeModule`), `ModuleContext`, `Intent` |
| `module.pack` | The shipped chips; `ArcadeModules.init()` registers them in init step 3. One sub-package per slot |
| `game` | `GameDef` (a cartridge), its codecs and schema, `CartridgeIds`, `CartridgeValidator`, `ResolvedGame` |
| `runtime` | The deterministic simulation: `MatchState`, `ArcadeRuntime`, `Rng`, `InputLog`, `Replay`, `HeadlessMatch`, `Budgets`, `ShippedReplays` (the sixteen attract runs in `data/neroarcade/arcade_replays/`) |
| `runtime.net` | Snapshot keyframe/delta codec and the snapshot / seat-input payload records |

A new chip is a class in `module.pack.<slot>` registered from `ArcadeModules`; it never needs a change to
`module`, `module.hook` or `runtime`.

### Shipped chips (43)

`ArcadeModules.init()` registers the seven empty-slot defaults (marked *), then each slot's
`<Slot>Pack.registerAll()` in slot order, each alphabetical by id.

| Slot | Chips |
|------|-------|
| ARENA | `grid_16`*, `grid_32`, `ring_24`, `strip_9` |
| PAWN | `cursor`*, `paddle`, `runner`, `serpent`, `ship`, `stacker` |
| RULE | `survive_time`*, `clear_board`, `last_standing`, `reach_exit`, `score_target` |
| HAZARD (stack of 3) | `bouncing_ball`, `chaser`, `falling_blocks`, `laser_sweep`, `shrinking_floor` |
| PICKUP (stack of 3) | `key`, `score_orb`, `shield`, `speed_boost` |
| CONTROL | `four_way`*, `one_button`, `twin_stick`, `two_way` |
| PACE | `steady`*, `pulse`, `ramp` |
| MODIFIER (optional) | `fog`, `low_gravity`, `mirror`, `wrap_around` |
| MULTIPLAYER | `solo`*, `coop`, `turns`, `versus` |
| THEME | `neon_void`*, `cindara_dusk`, `hangar`, `terminal_green` |

The 16 example cartridges in `common/src/test/resources/cartridges/` cover every chip;
`ExampleCartridgesTest` runs each one's scripted input to its outcome, replays it, and pins its final hash in
`cartridges/hashes.txt` as `<name>@<variant>`. Each one's attract variant ships as a `Replay` JSON in
`common/src/main/resources/data/neroarcade/arcade_replays/<name>.json` (the gallery seed and the client's attract
demo; author removed, credit "Gallery"). `ShippedReplaysTest` regenerates them into `build/arcade_replays/` and
fails if a shipped file differs or does not replay to its `@attract` pin; after a deliberate re-pin copy the
regenerated files over the shipped ones. The pseudo-player that made them never ships. A change to any chip's behaviour that moves a pinned hash is a
determinism change: re-pin only on purpose and say why in the change.

## Build

Stonecutter produces 27 cells (three loaders x nine Minecraft lines: 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10,
1.21.11, 26.1.2, 26.2, 26.3; source is written for 26.x, older lines use `//?` blocks and `legacyRenames`). Never run a plain `build`: the root
`:fabric`, `:forge` and `:neoforge` projects are vestigial containers. Build the cells explicitly:

```text
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build
./gradlew :fabric:26.2:ecjCheck
```

Both economy configurations must be green: the default (`neroeconomy_compat=true`, compiles the coin-op bridge
against NeroEconomy `neroeconomy_version`) and `-Pneroeconomy_compat=false` (no NeroEconomy artifact; the bridge, its
services line and the optional manifest entry are left out). Dev runs load NeroEconomy only with `-PwithNeroEconomy`.

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
  `PawnModule.opponentInput` (default `RuntimeOpponent.greedy`); it is an input, never logged, and re-derived
  by a replay. A pawn that overrides `opponentInput` is asked every simulated tick (A15.a). A pawn that keeps
  the default gets the greedy split: `RuntimeOpponent.target` is re-chosen every `OPPONENT_CADENCE` ticks and
  `RuntimeOpponent.steer` runs every simulated tick (both pure; the target is a runtime latch like the held
  input), so it can stop on any cell. The greedy treats VOID as blocking and, with nothing to do, heads for the
  board centre (an `AUTO_MOVE` pawn turns instead of reversing).
- **Respawn grace (A15.b).** Every housekeeping respawn sets `SeatState.SHIELD` to
  `max(remaining, Budgets.RESPAWN_GRACE_TICKS = 20)`; not at the initial spawn and not for a MULTIPLAYER chip's
  own `onRespawn`. A chip that re-serves a ball faster than 41 ticks after a goal must amend A15.b first.
- **Hidden is off the board (A15.c).** The collision pass (8), goal pass (8b) and projectile pass (8c) skip any
  entity carrying `Entities.F_HIDDEN`, on either side of a pair. Hide an entity to stop it colliding; never
  park it elsewhere.
- **A body's shots go with it (A15.d).** `MatchState.removeSeatBody` also removes every PROJECTILE the seat
  owns. Projectile movement stays a pawn duty.
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
  an `ABANDONED` or hot-seat result. A cabinet host gets `Boards.writer(server)`, the only caller of the
  package-private `LeaderboardStore.record`; a bench test play gets `BoardWriter.NONE`.
  `LeaderboardWriteOnlyFromMatchHostTest` scans the sources for any other `.record(` caller.
- **A bus is one simulation.** The host cabinet (first readable cartridge in) runs the only `MatchHost` and holds
  the bus's one awake slot; nodes hold a seat each (host = 0) and forward input into the host in-process, resolving
  the host every tick (never cached). **Assignment is sticky** (`BusAssignment`, review 003b F1): the incumbent host
  stays host while it holds a readable cartridge; a seated node keeps its index, unseated nodes and newcomers take
  the lowest free one; while the host's match runs nothing already on the bus changes; a linked stranger whose own
  match runs is left alone until it ends; a newcomer never pushes a member into overflow (`BusTopology.keeping`). A
  cartridge loaded from disk reads `insertedAt = Long.MIN_VALUE`. Nobody inserts into a node or a seated cabinet;
  only a player seated on the bus (or an op) ejects while anyone sits on it. A seat the host unbinds itself goes
  through `HostEnv.unseat` so the holder's seat and registry claim go too. Snapshots go once per viewer: one viewer
  group = players near any node or marquee plus every seat. A host or hub break ends the match `ABANDONED`; the
  simulation never migrates. Roles, seats and topology are runtime only; a cabinet saves only its bus-id hint.
- **Boards are keyed by cartridge id** (D-owner-1, 2026-10-23). Duplicates and downloads keep the id and share a
  board; a remix (new id) gets a fresh one; an author's in-place revision (same id) keeps it. `BusMatchResult
  .cartridgeId` is the key; `lineageRoot` is informational. `BoardPagePayload.cartridgeId`, `/neroarcade top`, the
  marquee, the link module (`BoardKeys.cartridgeKey` returns the id) and `export_cartridge` (by `id` only) all
  follow it. Saves write `"id"`; a pre-decision `"root"` is read as that id.
- **An imported id is kept only if the requester authored it here (Stage 7 security review H1).**
  `ArcadeActionHandler.importCartridge` keeps the payload's id only when the requester holds a draft with it or it is
  their own listing (`authoredHere`); any other valid id (another player's listing, draft or unpublished cartridge,
  a foreign server's `.nac`) becomes a remix: `CartridgeIds.remixLineage(lineage, id)` + `mintId()`. An id minted
  because the payload had none is kept. The link path is the only way to put an arbitrary id on an item, so never
  relax this (`ImportAuthorIsRequesterTest`). `export_cartridge` requires `id` (no by-root lookup, M2).
- **The push kill switch publishes nothing (M1).** With `pushNotifications.enabled=false`, `ArcadeEvents` records
  events in the `events` ring only and never calls `LinkEventBus` publish (NeroLink 1.x turns any published
  player-scoped event into a generic push); the record alert is still raised (Core's `LinkAlerts.raise` publishes
  its own `alerts` "raised" event, documented residue). The ring never holds `_notify`: only the bus copy carries
  it (L2). `PushKillSwitchTest` asserts zero published events while killed.
- **Names only with consent.** A leaderboard row stores a profile name only if the player turned credit on
  (`/neroarcade privacy credit on`, default off) when the row was written; turning it off strips every stored
  name (`CreditOptIn.onOptOut`). Rows expire after `privacy.leaderboardRetentionDays` (90, hard cap 365);
  personal bests after `privacy.personalBestRetentionDays` (365, hard cap 365) counted from when the best was
  **set** (a pre-Stage-6 best starts its clock at the first sweep). Both stores are registered in `NeroArcadeData`
  (`Boards.erasers()`).
- **Erasure covers every player-keyed store (Stage 6).** The composite walks `leaderboards`, `credit`, `exchange`,
  `drafts`, `benches` (`LoadedBenches`: the author/credit a loaded Game Bench's draft keeps after LOAD, best
  effort; an unloaded bench is the documented limit), `exports` (the export file + cooldown), `seats` (seat, bench
  and kiosk rate windows) and `link`, then calls each store's `backupNow` (with or without a server; stores resolve
  their target through `StoreAccess`, and `BackupHook.DIRECT` does nothing without a server).
  `PlayerKeyedStateInventoryTest` scans the sources for `extends SavedData` and UUID-keyed collection fields and
  fails on one that has no eraser or written reason; `ArcadeErasureConformanceTest` runs Core's `ErasureConformance`
  over the real erasers and walks a manual erasure end to end, backups included. A new player-keyed store needs an
  eraser, a probe there, an inventory entry and a PRIVACY.md line.
- **An erased seat records nothing (compliance review F1).** The `seats` eraser unseats with `DropReason.ERASED`;
  `MatchHost.unbind` with `ERASED` or `ADMIN` in `COUNTDOWN`/`PLAYING` nulls `matchSeats[runtimeSeatOf(i)]` before
  dropping the runtime seat, so `toBusResult`, the board and the link events never see that UUID
  (`ErasedSeatNeverWritesBackTest`). Walk-outs keep "best but no top row".
- **Update tags carry no builder identity (F3).** `CabinetClientInfo.anonymous` and `BenchDraft.preview()` strip both
  the author UUID and the credit; credit is shown only by the item tooltip, the Exchange page and `cartridge inspect`
  (`UpdateTagCarriesNoAuthorTest`). Credit opt-out also reaches every loaded bench (`LoadedBenches.stripCredit`,
  registered in `ArcadePrivacy.init()`).
- **Export.** `/neroarcade privacy export` (`ArcadePrivacy.export`, cooldown `privacy.exportCooldownSeconds`=60)
  writes `<world>/neroarcade/exports/<uuid>.json` (`PrivacyExport.build`, the requester's data only); files go after
  7 days (hourly sweep) and on erasure.
- **One match-end path.** After the board write, `MatchHost.finish` calls its `MatchListener` once (never for an
  `ABANDONED` match): `progression.ArcadeMatchEnds` fires `match_won` / `record_set` (scope = cartridge id, never a
  player; tallies live in `LeaderboardTable`), opens `first_versus_win`, counts one Exchange play per insertion
  (`MatchEnd.firstPlay`), then the tournament fan-out and the morale seam. A throwing listener is logged and skipped.
- **The Exchange.** Publish/unpublish = author or op; same id replaces the row (listingId, plays, hearts and
  publishedAt kept) only when the listing's author is the same or the publisher is an op (`NOT_OWNER` otherwise); the cap refuses, never evicts, and so does `privacy.listingsPerAuthor` (16, ops exempt);
  `/neroarcade admin unpublish <player>` purges one author; the kiosk tray is per viewer (the menu's own, returned on
  close), the kiosk holds no items; listing ids are never reused. Hearts are a count; who hearted is
  `ExchangeHearts` memory only. Credit is copied only if the author is opted in now, and stripped on opt-out. Erasure:
  `exchange` deletes the player's listings and tombstones rows naming them; `drafts` deletes their bucket and
  tombstones drafts of their games. A download or fetch mints the stored `GameDef` unchanged. Pages carry metadata
  only (`ExchangePagePayload`, never a `GameDef` or UUID). `exchange.enabled=false` refuses every op, store untouched.
- **A copy is a copy.** The duplicator writes the original's `GameDef` unchanged (id, lineage, revision, author,
  credit).
- **Seams fail soft.** A sibling is reached only through `compat/<x>/bridge` behind `META-INF/services`, looked up
  once after `isModLoaded`; `neroeconomy_compat=false` builds without the artifact. Coin-op is off by default and a
  throwing seam refuses the charge.
- **Seats are runtime only.** `Seat`, `SeatRegistry` and `AwakeCabinets` are memory; nothing about a seat is
  saved, exported or logged. One seat per player server-wide. Erasure unseats (`ArcadeServer.SEATS_ERASER`).
  Coin-op is charged after every seat check and immediately before the bind (`SeatTake.payThenBind`). No power for
  `sleepAfterTicks` ends a running match ABANDONED and drops every seat with `POWER` (a seam: 0.1.0 has no energy
  input and no power config key, so cabinets are always powered). A lobby whose bound seats are all ready but fewer
  than the chip's minimum starts with the seats present after `MatchHost.LOBBY_WAIT_TICKS`.
- **Runtime faults stay in the host.** `MatchHost` catches a throwing hook at start and step: the match ends
  ABANDONED (no board write, no listener), seats get `neroarcade.cabinet.msg.runtime_failed`, the log names the
  exception class and chip ids only, telemetry uses `captureHandledException(t, "runtime", op)`, and the runtime is
  discarded. Both `host.tick()` callers catch whatever still escapes and shut the host down (`DropReason.FAULT`).
  `CombinationFuzzTest` runs the chip product; a crash it finds is fixed in the chip.
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

## Stage 5 invariants (visuals, gallery, budget)

- **Renderers never draw blindly.** Every BER culls on its own range (`RenderPlan`: screens `screenRenderRange`,
  pixel text 16 blocks, holo-rack and platter 24), on the camera being in front of the face where it has one, and on
  NeoForge also on `getRenderBoundingBox`; a renderer with nothing to show (bench not editing, duplicator idle)
  returns false from `shouldRender`. Every screen-like surface draws through `CabinetScreens`' one pool
  (`TextureLru`, 24 textures). Every renderer reports to `ArcadeBerStats` (`/nsarcber`). Vertex counts live in
  `RenderPlan` and `RenderBudgetTest` holds them; the 20-cabinet + 2-marquee scene is ≤ 1 276 vertices.
- **Clients get only what a renderer needs.** The bench update tag carries the rack's slot-kind mask and the
  anonymous draft preview; the kiosk's carries `KioskBoard` rows (title + hearts); never a container, a UUID or a
  credit beyond the opted-in one the cabinet already sends.
- **Attract demos are recordings.** `AttractReplays` runs one `AttractPlayer` per shipped cartridge id that a
  screen drew in the last 2 s (≤ 16), one step per client tick; a cartridge without a shipped replay keeps the plain
  attract loop. Nodes and the marquee follow the host's demo.
- **The gallery is a test-world tool.** Operators, creative, nobody else within 96 blocks, never over a block with
  contents it did not place; every change is recorded in `GalleryStore` and `clear` restores exactly those states
  where the gallery's block still stands, discards its cartridges (never dropped) and unpublishes its listings
  (`GalleryCommands.GALLERY_AUTHOR`). It writes no board, counts no play and fires no milestone
  (`GallerySafetyScanTest`). Client command roots are `nsarcgallery` / `nsarcber`, never `neroarcade`.
- **Not done:** hiding the vanilla hotbar and crosshair while seated — no sibling has a per-loader precedent yet
  (NeoForge `RenderGuiLayerEvent.Pre`, Forge overlay cancel, Fabric HUD element replacement); revisit with Stage 8.

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
- **Opt-out Sentry telemetry** with no personal data. The DSN is NeroArcade's own Sentry project (EU ingest);
  keep the placeholder guard so a fork with the placeholder DSN stays silent.
- **`publish.yml` rules.** Direct-curl CurseForge upload with `max-parallel: 1` and the 5xx retry loop, plus
  the Modrinth v3 environment-metadata step after `mc-publish`.
- **Own creative tab** (`registry/ModCreativeTab`, id `neroarcade`, title `itemGroup.neroarcade`). Never add
  items to Core's tab.
- **Never commit, tag or push.** Leave version control to the owner.
- **The wiki is public.** Everything under `wiki/` is published as is; keep private planning material out of
  it and out of the repository.
- **No commercial game names.** Mini games, templates, items and docs use original names only.
