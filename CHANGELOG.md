# Changelog

All notable changes to **NeroArcade** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added — Stage 3 groundwork (2026-10-23)

- A new solid "block" cell for stacked pieces, drawn with its own tile; goal lines now draw as a dashed line in
  their seat's colour instead of looking like an exit. Every theme gained the stacker pawn sprite.
- A score rule no longer warns "nothing scores" when a hazard or pawn chip is what scores.
- The computer opponent now stops exactly on the orb it is after instead of hopping back and forth past it.

### Fixed — Stage 2 review (2026-10-23)

- SAVE never gives two cartridges the same id: a game saved onto a Blank always gets a new id (your own game's copy
  keeps its lineage, so it shares the leaderboard); only your own cartridge, back in the tray, is re-saved in place.
  Loading a cartridge with no recorded builder and saving it no longer adopts its id. Saving with someone else's
  cartridge or a different game in the tray is refused with a sentence that says what to do.
- Typing a title and pressing SAVE straight away no longer fails with "Give your game a title first."
- Taking a chip out of the rack and putting the same chip back keeps its settings.
- Closing the Game Bench stops your test play on the server too, even if the client never says so.
- A cabinet holding a cartridge shows READY and its attract loop (and glows) even while nobody has used it yet.
- The cartridge tooltip says "Remixed from another game" instead of showing a raw id.
- Nothing tells you to "press A" any more: the results card, the lobby and the bench name the key you bound.

### Changed — Stage 2 review (2026-10-23)

- Rack rows show a short effect with live values ("Survive 60 s · lives 1", "Moves 4 ways · speed 3"); the full
  description is on hover. The rack is 24 px wider.
- The HUD's amber line is the rule itself ("Survive 60 s · lives 1"), and "ESC leave" stays under the stats for
  the whole match. The legend says "2nd action" instead of "alt"; the Four-way chip no longer repeats the buttons.
- A bench test play names your actual keys while it counts down; TEST PLAY's hover lists them.
- Steady's "Divisor" is now "Slow-down". Neon Void's Variant setting is gone until the theme variants exist.

### Added — Stage 2: the core loop, client side (2026-10-21)

- **"Build your game"** — the Game Bench screen: ten rack rows, each with the slot's glyph and word in its colour,
  its chip slot(s) (`+` where a stack chip fits), and one line saying what the chip does, which default an empty
  slot uses, or (grey, with ▲) the warning that belongs to that row. Click a row to edit its chip with sliders,
  toggles and cycle buttons; type the title; TEST PLAY / STOP TEST, SAVE TO CARTRIDGE, LOAD, and ✕ CLEAR on rows
  holding chips loaded from a cartridge (amber dashed frame). Every disabled button says why on hover. A live
  preview runs the draft (fixed seed, scripted walk, 20 steps a second at most, paused when the game window is in
  the background) and restarts on every change; during a test play it shows the real match and the arcade keys
  steer it. Closing the screen stops a test play. The panel is 440 x 264 with the inventory inside it, so it fits
  a 1080p window at GUI scale 4.
- **Cabinet screens**: a 64x64 pixel screen drawn on the cabinet's tilted face from the theme's sprite sheets and
  the mod's 5x7 font — the match (interpolated between snapshots, your own pawn predicted), the countdown digit,
  the result banner, "NO CARTRIDGE", "NO POWER" (blinking), "UNREADABLE HERE" with the reason, "LINKED —
  WAITING", and the attract loop (title card, a demo walk around the board, high scores) on an idle cabinet. An
  asleep cabinet shows its dark glass. At most 24 screens hold a texture at once; screens draw only within
  `screenRenderRange` and only from in front.
- **Seated HUD**: the rule sentence, your score at 2x, the timer, other seats' scores; the control legend as
  keycaps with your own bindings until 5 s into play; the 3-2-1 countdown; the results card with rank, score and
  NEW BEST; NO POWER while paused.
- **Arcade controls** (Controls → NeroArcade): pad (W A S D), A (Space; also START in the lobby and on the results),
  B (Left Shift), aim (arrow keys). While seated the cabinet takes those keys and you do not walk; Esc leaves the
  seat instead of pausing. Input goes to the server on change plus a 5 Hz keep-alive, never more than 20 a second.
- **Sounds** for the seated player: pickup when your score rises, hit when you lose a life, a tick on the last five
  seconds, and the start/win/lose stings during a bench test play (the cabinet plays its own for everyone).
- Client-only config (`config/neroarcade.properties`, never synced): `client.scanlines` (CRT look, default off) and
  `client.screenRenderRange` (0 = the server's; can only shorten it).
- Privacy: a cabinet now tells nearby clients its cartridge's game definition so the screen can draw the right
  sprites — always with the author UUID removed.
- Tests: the renderer (pinned frame, status screens, whole-pixel scaling, scanlines), prediction (three-tick
  horizon, snap beyond one cell), input sending (on change, 5 Hz keep-alive, 20/s cap), the bench's words (every
  row, warning and disabled reason has a sentence), the preview cap and the screen-texture budget.

### Added — Stage 2: the core loop, server side (2026-10-21)

- Seven blocks with the asset contract's blockstate properties: the **Arcade Cabinet** (1x2) and the **Game
  Bench** work; the Cartridge Duplicator, Arcade Exchange, Marquee Screen, Link Bus and Link Cable place, render
  and carry tooltips saying what they will do (their behaviour arrives with linking). Marquee size cycles with
  sneak-use; cables connect to cables, the bus and cabinets.
- Items: one **Module Chip** item (the module is the `neroarcade:module_id` component; the model and tint come
  from `custom_model_data`), **Cartridge** (the game in the `neroarcade:game_def` component, kept as canonical
  NBT so a cartridge from a newer version survives untouched and reads "UNREADABLE HERE"), Blank Cartridge and
  Blank Chip. The creative tab lists the blocks, the blanks and one chip per registered module in slot order.
- The cabinet: insert a cartridge, use with an empty hand to play (solo starts on use), sneak-use to take the
  cartridge out. One seat per player server-wide; the seat drops when you walk more than `seatRange` (3 blocks)
  away, log out or press Esc. A cabinet sleeps after `runtime.sleepAfterTicks` with nobody near; at
  `runtime.maxAwakeCabinets` further cabinets queue. Status strip, pad and buttons mirror the game.
- `MatchHost`: the phase machine (idle, lobby, countdown, playing, results), snapshots to everyone within
  `screenRenderRange`, a single leaderboard write path that does nothing yet and never runs for an abandoned
  match.
- The Game Bench: a 14-chip rack plus a cartridge tray; chips are templates and are never used up. Edit
  settings and the title (chat-filtered), SAVE to a Blank Cartridge (re-saving your own cartridge keeps its id
  and bumps the revision; saving someone else's is a remix), LOAD a cartridge back into the rack, and TEST PLAY
  the draft on the bench itself.
- Seven payloads on `neroarcade:main` for all three loaders: seat input (sender must hold the seat; 30 per
  second, excess dropped and counted, never a kick), leave seat, bench edit, snapshot, match phase, HUD legend
  and bench state. Client screens, the cabinet renderer and the HUD arrive next.
- Commands: `/neroarcade cartridge inspect` and `/neroarcade admin unseat <player>`.
- Recipes for every block, the blanks and the seven shipped chips (Core's Nero Alloy in the cabinet, bench,
  duplicator, exchange, bus, blank cartridge and the pawn chip); loot tables for both data formats; sounds.
- Config: `seatRange` (3.0, 1.5..8.0).
- Tests: match host lifecycle and single write, input acceptance and rate, seat exclusivity, sleep rule, awake
  cap, bench draft, cartridge component, data files and the asset contract.

### Added — Stage 1: module contract and deterministic runtime (2026-10-20)

- The module contract (`module`, `module.hook`): ten fixed slots (`ModuleType`), the `ArcadeModule` property
  bag (INT/BOOL/ENUM only), ten pure hook interfaces, capability flags, a module registry that freezes at
  init (common init step 3, before the creative tab) and grey bench warnings derived from flags alone.
- The seven empty-slot default chips: `arena/grid_16`, `pawn/cursor`, `rule/survive_time`,
  `control/four_way`, `pace/steady`, `multiplayer/solo`, `theme/neon_void`. A missing default fails init.
- Cartridges as data (`game`): `GameDef` with its codec (schema 1), 16-character base32 cartridge ids with
  remix lineage, and a fail-closed validator with thirteen ordered checks, an 8 KiB NBT cap and a 12 KiB
  JSON import cap. The `neroarcade:game_def` item component is registered in Stage 2.
- The deterministic runtime (`runtime`): integer-only match state with id-sorted entities, SplitMix64
  RNG, the fixed hook order per tick, pace divisors, collisions, lives/respawn, a hard match-length stop,
  runtime opponents, input logs, replays and a headless match harness.
- Snapshot keyframe/delta codec (`runtime.net`) with a byte cap and keyframe fallback, plus the snapshot and
  seat-input payload records (registered with their handlers in Stage 2).
- `runtime.*` budget config keys, `screenRenderRange`, `cabinetsNeedPower` (default off) and
  `cabinetPowerPerTick`, all server-authoritative.
- Lang for every slot, default chip, cartridge error and bench warning.
- Tests: contract, codec, validator, schema, ids, RNG vectors, state hash, replay determinism with pinned
  hashes, snapshots, budgets, tick order, odd combinations, prediction and no-floats checks.

### Changed — Stage 1 review fixes (2026-10-20)

- Hits are decided by the pawn and applied by the runtime: `PawnModule.onHit` returns `HitOutcome`
  (DAMAGE / DEFLECT / IGNORE); hazards gain `onDeflected`, `onShot` and `onGoal`; a damaging projectile is
  spent; pawn-vs-pawn hits are directional (head into body). New collision steps: goal cells
  (`Cell.GOAL_0..3`) and projectiles vs hazards. `ArenaModule.tick` runs after the pace each simulated tick.
  The runtime opponent is now driven by the pawn chip (`opponentInput`, default `RuntimeOpponent.greedy`)
  instead of a random walk, and the headless harness no longer scripts or logs its seat.
  `MatchState.solidAt` lets pawns treat `F_SOLID` entities as blocking. The pinned replay hashes are
  unchanged: no example cartridge uses a runtime opponent, projectiles, goal cells or solid entities.

### Added — scaffold (2026-10-20)

- Multiloader skeleton for NeoForge, Forge and Fabric on Minecraft 26.1.2, 26.2 and 26.3 (nine cells),
  built on Neroland Core 1.15.0 with the Core floor derived from the compiled version.
- Numbered common init, platform seams (`PlatformInfo`, `NetworkPlatform`), the `neroarcade:main` network
  channel (no payloads yet), the NeroArcade creative tab (empty) and `config/neroarcade.properties`.
- Per-player data-erasure registration with Core (no player data is stored yet).
- Opt-out, anonymous crash telemetry wiring; inert until a Sentry project DSN is configured.
- Logo, CI, publish and wiki workflows.
