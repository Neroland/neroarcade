# Changelog

All notable changes to **NeroArcade** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- On Minecraft 26.3, left clicks on the Game Bench's module property controls work again (right-click still steps a choice backwards).

## [0.1.0-beta.1] - 2026-10-24

The first build with gameplay: a mini-game construction kit. Players build a game from module chips at a Game
Bench, save it to a cartridge, play it on an Arcade Cabinet, link cabinets for versus and co-op, and copy, publish,
download and remix each other's games. Requires **Neroland Core 1.15.0** or newer (below 2.0) and Java 25.
NeroEconomy (0.4.0-beta.1 or newer) and the NeroLink bridge are optional.

This build is feature-complete and has passed its automated tests (740) on all nine cells; it has **not yet been
verified in a live game**. The scope cuts, known gaps and placeholders are listed at the end of this entry. The
stage-by-stage development record is kept in `BUILD-HISTORY.md`.

### Added

- **Ten-slot games.** A game is ten fixed slots — Arena, Pawn, Rule, Hazard, Pickup, Control, Pace, Modifier,
  Multiplayer and Theme — each filled by one **module chip** (Hazard and Pickup take up to three). An empty slot
  uses its documented default (Grid 16, Cursor, Survive 60 s, Four-way, Steady, Solo, Neon Void; no hazard, pickup
  or modifier). Every chip has plain-language settings (whole numbers, switches and choices only).
- **43 module chips**: 4 arenas, 6 pawns, 5 rules, 5 hazards, 4 pickups, 4 controls, 3 paces, 4 modifiers,
  4 multiplayer shapes and 4 themes. Each is crafted from a Blank Chip, its slot's reagent and (for all but the
  defaults) one more item; chips are templates and are never used up. Every chip has original sprites and a
  description, and appears in the gallery.
- **Game Bench** ("Build your game"): ten rack rows that say what each chip does with its live values, a settings
  panel per chip, a title box (chat-filtered, 32 characters), grey warnings for odd combinations, a live preview,
  TEST PLAY on the bench itself, SAVE TO CARTRIDGE and LOAD. Every disabled button says why. While someone edits,
  the bench shows a hovering holo-rack of the ten chips.
- **Cartridges.** A cartridge is a game definition, never code: an id, a title, the chips and their settings, a
  lineage of the games it was remixed from, and a revision. At most 8 KiB, checked by one fail-closed validator on
  every insert, copy, publish, download and import. A cartridge that uses a chip this server does not have reads
  "UNREADABLE HERE" with the reason; it is kept, never dropped.
- **Arcade Cabinet.** Insert a cartridge and use the cabinet with an empty hand to play. The game runs on the
  server — a fixed 20-step-a-second simulation with whole-number state, a seeded random stream and a replay of
  every match — and every player nearby watches the same 64 × 64 screen. The seated player gets a HUD (rule, score,
  timer, key legend, countdown, results card with NEW BEST) and arcade controls (pad W A S D, A Space, B Left
  Shift, aim on the arrow keys; Esc leaves the seat). An idle cabinet plays an attract loop; a cabinet with
  nobody near sleeps and frees its slot.
- **Link Bus, Link Cable and Marquee Screen.** Up to four cabinets on up to 16 cables play one game: the cabinet
  holding the cartridge runs it, the others are extra seats. Versus, co-op (shared or own lives) and hot-seat Turns
  for 2–4 players; a game with fewer players than seats starts ten seconds after everyone present is ready (the
  lobby counts it down), so a Versus game with one player brings in the computer opponent and a Co-op game with
  one player is played solo. A Marquee Screen (sizes 1–4) shows the bus's game with every score and rolls the top
  ten between games.
- **Leaderboards.** Every cartridge id has a top-ten board, and every player a personal best on it. Copies and
  downloads share the board; a remix starts its own. Only the match host writes a row, once per finished match.
  `/neroarcade top` shows the board of the cartridge in your hand or the cabinet you look at.
- **Sharing.** The **Cartridge Duplicator** copies a cartridge onto a Blank Cartridge (same game, same board, same
  credit). The **Arcade Exchange** lists every game published on the server (most hearted first, title, genre
  words, the ten slots, hearts and plays): publish, unpublish your own, download onto a Blank, give a heart, and
  fetch drafts sent from the companion app. **Remix** is a save from a bench loaded with someone else's game: the
  new cartridge gets a new id and remembers the original in its lineage.
- **Sixteen example games** (Paddle Duel, Serpent Garden, Stack Nine, Drone Maze, Lane Hopper, Hot-Seat Orbs,
  Tandem Sweep, Vault Run, Gunner Ring, Shrink Island, Mirror Pulse, Float Lane, Four-Way Rally, Crate Catch,
  Blind Tube, Stack Duel) that between them use every chip, each with a recorded attract demo.
- **Gallery.** `/neroarcade gallery [clear]` (operator, creative) builds a lit showcase hall — one cabinet per chip,
  a workshop dais stocked with the sixteen example games, and a four-cabinet link row under a Marquee Screen — and
  puts back exactly what it changed. `/nsarcgallery capture [time]` shoots the gallery screenshots;
  `/nsarcber [reset]` prints the renderer cost per frame.
- **NeroLink companion app module** `neroarcade`: your library, leaderboards by cartridge id, the cabinet you sit
  at (a label, never a coordinate), your recent events and the wiki; export a game as a file and import one into
  your own Exchange drafts; a "Record beaten" notification that names only the game and your new rank, and a daily
  "Cartridge downloaded" digest. Off in the app until the player turns the Arcade category on.
- **Progression.** Milestones `neroarcade:first_cartridge`, `first_published` and `first_versus_win`; world
  channels `neroarcade:match_won`, `cartridge_published` and `record_set` (scoped to a game or the Exchange, never a
  player); an "Insert Coin" quest chapter for NeroQuests shipped as data (ten quests).
- **Integrations.** Optional coin-op seats and paid downloads through NeroEconomy (`economy.coinOp`, off by
  default); theme colours registered with Neroland Core's palette; Neroland Core item highlights; quiet seams for
  tournaments, soundtracks and colony morale that do nothing until those mods offer an API.
- **Commands.** `/neroarcade cartridge inspect`, `/neroarcade top`, `/neroarcade privacy credit [on|off]`,
  `/neroarcade privacy export`, `/neroarcade gallery [clear]`, `/neroarcade admin awake`,
  `/neroarcade admin unseat <player>`, `/neroarcade admin unpublish <player>`; client `/nsarcgallery capture [time]`
  and `/nsarcber [reset]`.
- **Assets.** Seven blocks with original models, 16 × 16 textures, four screen palettes and 23 synthesised sounds;
  recipes for every block, blank and chip; loot tables; the wiki bundled into the jar for the companion app.

### Changed

- **New logo and mod icon:** the family core-prism now plays on a neon arcade cabinet, with a pixel-font wordmark.
  The Modrinth and CurseForge pages are rewritten to match the other Neroland mods.
- The mod version moves from the `0.0.1-alpha.1` scaffold to `0.1.0-beta.1`. Neroland Core is pinned at 1.15.0 and
  every loader manifest floors it there.
- **Leaderboards belong to a cartridge id**, not to a game's lineage: a remix with easier rules can no longer post
  onto the original's board. A board saved by an earlier development build under its lineage root loads as the
  board of that id.
- **Personal bests expire** a year after they were set (`privacy.personalBestRetentionDays`, default and hard cap
  365; beating the best restarts the clock). Top-ten rows keep their 90-day default.
- Rules of play settled during review: a 20-tick respawn shield; hidden things never collide; a pawn's shots vanish
  with it; the Runner's landing locks its next hop for four ticks; the Laser Sweep starts far from every player and
  carries a moving gap; the computer player steers every tick and never steps into the void.

### Fixed

- **Worlds load on every Minecraft 26.x line.** The Ring 24 arena chip's recipe asked for `minecraft:chain`, an
  item Minecraft has since renamed `minecraft:iron_chain`. One unreadable recipe stops the whole recipe list
  loading, so creating or opening a world hung on "Preparing world creation". The recipe now uses the iron chain.

Found and fixed by the design, compliance and security reviews before release:

- SAVE can no longer take over another builder's cartridge id or give two cartridges the same id.
- A running Link Bus keeps its host and every seat when a block changes nearby or the server restarts; nobody can
  push a cartridge into a linked or occupied cabinet, and only a seated player (or an operator) can take the host's
  cartridge out while anyone sits on the bus.
- Two players at one Exchange kiosk can no longer take each other's cartridges (the tray is each viewer's own).
- A player whose data is erased in the middle of a match is never written back as a score.
- Closing the Game Bench always stops its test play; a re-inserted chip keeps its settings.

Fixed — final review:

- **A chip that throws on the server no longer takes the server down.** The match ends abandoned (nothing is
  written to a leaderboard), the players see "This game stopped with an error; the cartridge may use a combination
  this server cannot run.", and the server log names only the error type and the chips. The cabinet does not try
  again until someone presses START.
- **A Stacker piece hard-dropped into an empty column on a board that wraps top to bottom** (the Wrap-around
  modifier on the vertical axis) no longer falls forever and freezes the server; after one full lap it locks where
  it is. Found by a new test that plays every arena and pawn with stacked chips at their extreme settings.
- **A linked game no longer waits forever** when fewer players than the game's seats are ready: two friends on a
  four-cabinet bus can play a four-seat Versus game, and one player can play a Co-op game.
- The gallery's showcase listings no longer count towards the "games published" quests.
- The Fabric manifest asks for Java 25, the version the mod is built for.
- The two cabinet power settings were removed (see "Not in this release").

### Security

- Everything that decides a game runs on the server. Input is accepted only from the player who holds the seat,
  for the current match, at most 30 packets a second (excess is dropped, never a kick). Display cabinets cannot
  affect the host.
- Leaderboards are written by one path only: the match host, once per finished match, never for an abandoned or
  hot-seat match.
- Cartridges are data and fail closed: an unknown chip, an over-budget setting, a cartridge over 8 KiB (12 KiB as an
  imported file) or a lineage over eight entries is refused, never repaired.
- Companion-app imports keep a cartridge id only when it is the requester's own draft or listing; any other id
  becomes a remix with a fresh id and its own board. Export requires the cartridge id. Both actions are rate-limited
  and idempotent, and can each be switched off.
- Publishing replaces an existing listing only for its author or an operator; one player may list at most 16 games
  (`privacy.listingsPerAuthor`); `/neroarcade admin unpublish <player>` removes everything one player listed.

### Privacy

- **Credit is opt-in** (`/neroarcade privacy credit on|off`, off by default). Without it a player is "Anonymous
  builder" on games and "Anonymous" on boards; turning it off strips the name from every board, listing, draft and
  loaded bench on the server.
- Cabinets and benches never send a builder's id or credit to bystanders. Seats and matches are memory only; no play
  history is kept.
- **Retention:** top-ten rows 90 days and personal bests 365 days (both hard-capped at 365), imported drafts
  30 days, export files 7 days.
- **Export:** `/neroarcade privacy export` writes everything NeroArcade holds about you to one file in the world
  folder (once a minute).
- **Erasure:** Neroland Core's `/neroland data eraseme` (and the operator's `erase`) reaches every NeroArcade store —
  boards, bests, credit, Exchange listings and hearts, drafts, loaded benches, seats, the export file and the
  companion-app data — and refreshes each store's backup. Cartridges that are already items keep what they carry.
- **Notifications** carry a game title and a rank or a count, never who or where. `pushNotifications.enabled=false`
  publishes nothing to the NeroLink bridge.
- **Anonymous crash reports** for errors in NeroArcade's own code (Sentry, EU servers; no names, UUIDs, IPs or
  world data; at most 10 per session). On by default; opt out with `telemetryEnabled=false` in
  `config/neroarcade.properties`. `PRIVACY.md` is the full disclosure.

### Not in this release (scope cuts)

- **0.2.0:** the Holo-Arena (playing a game out in real blocks at world scale), tournaments, a cross-server
  exchange, NeroNotes soundtrack chips and sprite editors. Tournaments and soundtracks have quiet seams only.
- The companion app's **"Challenge a friend"** action: there is no way yet for both players to agree to it.
- **Coin-op is built but disabled by default** (`economy.coinOp=false`); it needs NeroEconomy and an operator who
  turns it on.
- **Hiding the vanilla hotbar and crosshair** while a player is seated.
- **Turns across a Link Bus.** A Turns game is hot-seat at the cabinet holding the cartridge, and is never recorded
  on a leaderboard.
- **More than four seats** on one game or one bus.
- **The Marquee multiblock.** The Marquee Screen is one block that scales its picture from 1 to 4.
- **Per-axis wrap set by an arena.** Ring 24 always wraps left to right; the Wrap-around modifier chooses its axes.
- Theme variants (Neon Void's "Variant" setting was removed until they exist); the cartridge item's per-game glyph
  is decorative.
- Global or cross-server leaderboards; a record of which games a player played.
- **Cabinet power:** cabinets and the duplicator do not draw Core energy in this version, and there is no power
  setting; the NO POWER state exists for a later release.

### Known gaps in this build

- **Runtime verification is pending**: the screens, the gallery, linked play, the render budget and the phone
  notifications have been tested in code, not yet in a live game.
- A paddle that does not move returns a ball that comes off a corner along the same diagonal, so a Paddle Duel
  between two idle players can stall until the match's time limit.
- Recipe-book entries unlock when you first hold one of a recipe's ingredients.
- Hearts are deduplicated per server session, and an Exchange listing's play count can rise again when a cartridge
  is taken out and put back in. Both are signals, not scores.
- Erasure cannot reach cartridges that are already items, or a Game Bench in an unloaded chunk; `PRIVACY.md` says
  so.
- Rich notification text needs NeroLink 1.1.0 or newer (older bridges show a generic "events update"). With
  `pushNotifications.enabled=false`, a NeroLink 1.x bridge may still announce the stored record alert with a
  generic "alerts update" that names nothing.
- The gallery capture and `/nsarcber` messages are English only.

### Placeholders

- **"Insert Coin"** is the quest chapter's working title.
- The quest pack has not yet been registered in NeroQuests' own sibling-pack test; that registration happens in the
  NeroQuests repository.
