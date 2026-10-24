# Changelog

All notable changes to **NeroArcade** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-beta.1] — 2026-10-23

The first build with gameplay: a mini-game construction kit. Players build a game from module chips at a Game
Bench, save it to a cartridge, play it on an Arcade Cabinet, link cabinets for versus and co-op, and copy, publish,
download and remix each other's games. Requires **Neroland Core 1.15.0** or newer (below 2.0) and Java 25.
NeroEconomy (0.4.0-beta.1 or newer) and the NeroLink bridge are optional.

This build is feature-complete and has passed its automated tests (740) on all nine cells; it has **not yet been
verified in a live game**. The scope cuts, known gaps and placeholders are listed at the end of this entry. The
per-stage detail of how the build got here follows under "Build history".

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
- Anonymous, opt-out crash reporting is wired but inert in this build (see Placeholders). `PRIVACY.md` is the full
  disclosure.

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

- **Crash reporting is inert:** the Sentry DSN is a placeholder until NeroArcade has its own reporting project, so
  nothing is sent.
- **"Insert Coin"** is the quest chapter's working title.
- The quest pack has not yet been registered in NeroQuests' own sibling-pack test; that registration happens in the
  NeroQuests repository.

### Build history

The development record, stage by stage, newest first.

#### Fixed — security review (Stage 7, 2026-10-23)

- **Importing a cartridge from the NeroLink app can no longer borrow someone else's id** (and with it their
  leaderboard). The id in an imported cartridge is kept only when it is one of your own drafts or your own listing;
  any other id — another player's listing, another player's unpublished cartridge, or a cartridge from another
  server — becomes a remix with a fresh id and its own board.
- **`pushNotifications.enabled=false` now really stops NeroArcade's phone notifications.** With the switch off the
  module publishes nothing to the NeroLink bridge (which turned every published event into a generic notification);
  events are kept for the app's events list only. The record alert is still stored; a NeroLink bridge may still
  announce it with a generic "alerts update" that names nothing. PRIVACY.md and USING-NEROLINK.md say so.
- `export_cartridge` requires the cartridge `id`; the old lookup by lineage root alone is gone (a lineage can be
  claimed by anyone's cartridge).
- The leaderboard section no longer reveals an unpublished cartridge's lineage root to someone who never played it.
- The events list no longer repeats each event's notification text.
- An import whose title the chat filter has not judged yet is refused with "title pending review; try again"
  instead of being accepted and then silently removed.

#### Fixed — compliance review (Stage 6, 2026-10-23)

- **A player whose data is erased in the middle of a multi-seat match is no longer written back.** The erasure
  unseats them with the new "Your data was erased." reason, and the match forgets who sat there, so when the others
  finish it records no personal best, no top row and no companion-app event for them. An operator's
  `/neroarcade admin unseat` gets the same treatment; walking out still keeps your personal best.
- **Cabinets and Game Benches no longer send a builder's credit to bystanders.** The block data every nearby client
  receives carries the game without its author id and without its credit (no client drew it there).
- **Turning credit off now also clears it from every loaded Game Bench** whose draft came from your cartridge.
- The privacy export's `you_are` for a listing is now always `author` (a listing's author is the game's author).
- An erasure requested off the server thread now waits for the Game Bench walk to finish before reporting success.
- PRIVACY.md and the Privacy wiki page name the NeroLink relay (`nerorelay.neroserver.xyz`) and its retention, say
  that every Exchange listing of your game is removed on erasure (whoever listed it), and drop the cabinet's
  "attract screen may show the credit" clause.

#### Fixed — Stage 5 review (2026-10-23)

- **Arcade Exchange: every slot is named, not just coloured.** The selected game's ten slots are now 10×10 cells
  with the slot's glyph (grey when empty); hovering a cell shows the slot word and the chips in it, or "Empty — the
  game uses the default". Hearts and plays moved under the grid. (Network: each Exchange row now also carries the
  chip ids in each slot — ids only, never a game.)
- An unreadable listing explains why on hover in the list, before you press Download.
- **Cartridge Duplicator:** the result line shows on up to two lines and no longer overlaps the inventory (the
  "Inventory" label is gone). The no-power and unreadable refusals are shorter so they fit.
- **An Arcade Cabinet with no power no longer gives off light** (it lit up at level 7 like a powered one).
- Wiki, Gallery: the hall's floor is at your feet's height, and items left in the gallery's Game Bench, Cartridge
  Duplicator or Exchange tray are removed when the gallery is cleared.
- The Exchange kiosk's screen no longer builds its list every frame, only when it redraws.

#### Added — privacy and compliance (Stage 6, 2026-10-23)

- **`/neroarcade privacy export`**: writes everything NeroArcade holds about you (personal bests and your top-ten
  rows, the listings you published or authored, your drafts, your credit setting) to
  `neroarcade/exports/<your id>.json` in the world folder and tells you the path. Once a minute
  (`privacy.exportCooldownSeconds`); the file is deleted after 7 days and by an erasure request.
- **Personal bests now expire** a year after they were set (`privacy.personalBestRetentionDays`, default and hard
  cap 365); beating your best restarts the clock. Bests from earlier builds start their clock at the first sweep.
- **Erasure reaches the Game Bench:** the author and credit a loaded bench's draft remembers after LOAD are replaced
  by the anonymous marker. Erasure also forgets your bench edit rate window and your export file.
- The bench's "Saved as Anonymous builder" line explains on hover how to turn credit on.
- New wiki page: Privacy.

#### Changed — privacy and compliance (Stage 6)

- Re-publishing a game id that is already listed replaces the listing only for its own author or an operator; anyone
  else is refused ("already listed by someone else").
- `privacy.leaderboardRetentionDays` keeps its hard cap of 365 days, now stated loudly in the config comment.
- Every store's backup copy is refreshed straight after an erasure step, and the erasure suite now runs Neroland
  Core's conformance harness over every NeroArcade store.
- PRIVACY.md rewritten in full: what is and is not stored, the block-data limit, legal basis, retention, export,
  erasure, credit consent, kill switches, the companion app and the push relay, crash reporting (including the
  loaded-mod list it would send), contact and changes.

#### Added — visuals and the gallery (Stage 5, 2026-10-23)

- **`/neroarcade gallery`** (operators, creative mode, nobody else within 96 blocks): builds a lit showcase hall
  north of you — one cabinet per module chip (43) with a floor sign naming the chip in its slot colour, a workshop
  dais (Game Bench, Cartridge Duplicator, Arcade Exchange stocked with the sixteen example games, credit
  "Gallery") and a four-cabinet link row under a scale-3 Marquee Screen. Every screen plays its game's recorded demo,
  so the hall is alive with nobody seated; the marquee shows the link row's four-player demo mid-match.
  `/neroarcade gallery clear` puts back exactly the blocks the build changed (remembered in the world save), takes
  the gallery's Exchange listings down and never drops a cartridge. The build refuses to cover any chest or machine.
  See the wiki's Gallery page. Paper-style hybrid servers are not supported.
- **`/nsarcgallery capture [time]`** (client): freezes time and weather, builds the gallery, clears dropped items and
  shoots the Modrinth gallery frames to `screenshots/neroarcade/` with the HUD hidden, plus a `budget.txt` of the
  renderer numbers. **`/nsarcber [reset]`** (client) prints NeroArcade's renderer cost per frame.
- **Attract demos:** the sixteen example cartridges ship their attract runs as recordings
  (`data/neroarcade/arcade_replays/`). A cabinet holding one of those games (or a copy) plays the recording between
  its title card and its high scores; a linked bus's nodes and marquee follow the host.
- **Game Bench holo-rack:** while someone edits, ten chips hover over the holo-table in their slot colours (grey when
  the slot is empty), bobbing, with a live preview of the game above them.
- **Cartridge Duplicator:** the platter spins while it copies, a cyan bar fills on the front, and a finished copy
  flashes green.
- **Arcade Exchange kiosk:** the screen scrolls the top games with their hearts ("NO LISTINGS" when empty) under an
  "XCHG" header. Only titles and heart counts reach a kiosk.
- **Cabinet marquees** show the cartridge's title (or NEROARCADE) in the arcade's pixel font.
- **Recipe book:** every NeroArcade recipe unlocks when you first hold one of its ingredients (module chips unlock
  with the Blank Chip).
- **Item highlights:** NeroArcade items carry Neroland Core's slot-border highlight (machines, cartridges, chips,
  blanks).

#### Changed — Stage 2 polish (2026-10-23)

- Bench: the credit line reads "Saved as Anonymous builder"; a stacked row with two or three chips reads
  "Falling block + 1 more"; the loaded-chip hint and the STOP TEST help are plain sentences.
- Bench sliders: the mouse wheel steps by a sixtieth of the range (Seconds by about ten), a drag updates the preview
  while you drag (at most ten times a second), and a slider is never rebuilt under the mouse. During a test play
  the controls say why they are off.
- The Blank Chip tooltip says how chips are made; the chip tooltip names its slot ("Up to three Hazard chips fit in
  one game.").
- A lone player back in the lobby after a game is asked "Ready for another go?".
- "Already seated" names the coordinates as X, Y, Z; a missing module tells you to ask the server owner.
- Removed eight unused text keys.

#### Performance — render budget (2026-10-23)

- Every renderer culls by distance and by facing (never a blanket draw) and counts its frames; screens share one
  pool of 24 textures. The 20-cabinet + 2-marquee scene is at most 1 276 vertices a frame (the family gate is
  60 000). Not done: hiding the hotbar and crosshair while seated (no per-loader precedent in the family yet).

#### Changed — leaderboards per cartridge id (2026-10-23)

- **A leaderboard belongs to one cartridge id.** Duplicates and downloads keep the id and share its board; a remix
  (a new id made from someone's game) starts a fresh board, so a remix with easier rules can no longer post onto the
  original's board. An author's own in-place revision keeps its board. `/neroarcade top`, the marquee and attract
  screens, the `match_won` / `record_set` world counts and the NeroLink module all follow the id.
- `BoardPagePayload`'s first field is now `cartridgeId` (it was `lineageRoot`; same wire format, a string).
- Saved boards are written under `id`; a board saved before this change (under `root`) loads as the board of that
  id — nothing crashes, nothing is lost for an original game.
- NeroLink `export_cartridge` looks a cartridge up **by `id`** (own draft, else the published listing of that id);
  only a request without an `id` still uses the lineage root.

#### Fixed — Stage 4 review (2026-10-23)

- **Placing or breaking a block beside a running bus no longer drops players.** The bus keeps its host and every
  seated player's seat across rediscoveries; nothing already on the bus changes while a match runs; after a
  restart, a cartridge pushed into a free cabinet no longer takes the bus over; linking a bus to a cabinet with a
  match in progress leaves that match alone; a newly placed cabinet never pushes a linked one off a full bus.
- A cartridge can no longer be pushed into a linked cabinet or a cabinet someone else sits at ("This cabinet is
  linked — the host cabinet holds the bus's cartridge.").
- While anyone sits on a bus, only a player seated on it (or an operator) can take the host's cartridge out.
- A bus that shrinks under a seated player now frees their seat completely (they could not sit anywhere else until
  they pressed Esc).
- A host that unloads or vanishes now releases the seated players' controls at once.
- **Arcade Exchange:** one player may list at most `privacy.listingsPerAuthor` games (16; operators exempt), and
  `/neroarcade admin unpublish <player>` removes everything one player listed. The kiosk tray is now each viewer's
  own and comes back to them when they close the kiosk (two players at one kiosk could take each other's
  cartridges); a tray item saved in an older kiosk drops out the next time it is opened or broken.
- A publish whose name check finishes after the kiosk was closed is dropped, and the "first published" milestone
  goes to the player who published.
- Coin-op is charged after every other check and right before the seat is taken, so a refused charge never starts
  the countdown.
- A match without power for `runtime.sleepAfterTicks` now ends (abandoned, nothing recorded) and every seat drops
  with the "no power" message.
- Erasing a player's data now also unseats them.
- Fewer full-frame updates when a spectator keeps stepping in and out of a cabinet's range.

#### Added — NeroLink companion app module (Stage 7, mod side, 2026-10-23)

- **NeroLink module `neroarcade`** (registered last in common init; a failure logs one warning and the arcade keeps
  working). Sections: `library` (your drafts, your published games, boards where you have a best; 50 per page),
  `leaderboard?cartridge=<id>` (opted-in names only, your own row unnamed, 30 a minute), `cabinets` (only the
  cabinet you sit at, labelled "Overworld · Linked hall (3 cabinets)", never a coordinate), `events` (your last
  20) and `wiki`.
- **Actions:** `export_cartridge` (any published game or your own draft, as data; other authors anonymised) and
  `import_cartridge` (a game file into your own Exchange drafts through the same validator as the bench and the
  chat filter; 3 a minute, 10 an hour). Both idempotent by request id, rate-limited and individually switchable
  (`link.actions.*`).
- **Events and notifications** on `neroarcade.events`: *Record beaten* (the game and your new rank only — never who
  or where; at most one per game per hour), *Cartridge downloaded* (a daily digest at `link.digestHour`, at most
  five per author) and match results (in the app, never a notification). `pushNotifications.enabled` is the kill
  switch for notification text.
- Leaderboards in the app are addressed by **cartridge id** (copies share a board; a remix has its own).
- Exchange listings count downloads since the last digest (a number per listing, never who).
- Config: `link.enabled`, `link.actions.export_cartridge`, `link.actions.import_cartridge`, `link.importPerHour`,
  `link.push.recordCooldownMinutes`, `link.digestHour`, `link.libraryPageSize`, `pushNotifications.enabled`.
- The `wiki/` pages are bundled into the jar (with a generated `index.json`) for the app's wiki.

#### Not shipped

- The "Challenge a friend" app action: there is no way yet for both players to agree to it.

#### Added — copying, the Arcade Exchange, progression and integrations (Stage 4B, 2026-10-23)

- **Cartridge Duplicator** ("Copy a game"). Put a cartridge and a Blank Cartridge in, press Copy: three seconds
  later you have the original and an identical copy (same game, same leaderboard, same credit). Every refusal is
  one sentence.
- **Arcade Exchange** ("Find a game to download"). Every game published on the server in one list, most hearted
  first, eight to a page: the title, genre words in their slots' colours, the ten slots, hearts and plays, and
  "Anonymous builder" unless the author turned credit on. Publish your own cartridge from the tray or your hand
  (the title goes through the server's chat filter), unpublish your own, download onto a Blank Cartridge, give a
  heart (once per game per session, 20 an hour, never your own). A **My drafts** tab holds games sent from the
  NeroLink app (up to 8, kept 30 days). At most 200 listings; a full Exchange refuses rather than deleting anyone's
  game. `exchange.enabled=false` switches it off.
- **Progression.** Personal milestones `neroarcade:first_cartridge`, `first_published` and `first_versus_win`, and
  server-wide channels `neroarcade:match_won`, `cartridge_published` and `record_set` (scoped to a game or to the
  Exchange, never to a player). An "Insert Coin" quest chapter for NeroQuests ships as data; finishing "Saved to
  Cartridge" hands you two Blank Cartridges.
- **Integrations.** Optional coin-op play and paid downloads through NeroEconomy (`economy.coinOp`, off by
  default); theme colours registered with Neroland Core's palette; quiet hooks for tournaments, soundtracks and
  colony morale that do nothing until those mods offer an API.

#### Changed — Stage 3 review (2026-10-23)

- **Respawn grace.** Every respawn comes with 20 ticks of shield, so a drone or a beam can no longer camp the
  spot where you come back. Your very first spawn has none.
- **Hidden things are off the board.** A hidden beam cell, pickup, hazard or shot never touches anything.
- **A pawn's shots go with it.** When a pawn goes down (or its hot-seat turn ends) its shots vanish.
- **Paddle computer player.** It now reads the ball's bounce off the side walls and decides every tick, so it
  settles under the ball instead of overshooting.
- **Bouncing Ball.** A moving bat can no longer flatten the ball into a straight up-and-down shot; it always
  keeps some sideways speed.
- **Laser Sweep.** The beam starts on the line farthest from every player and carries a gap (a quarter of the
  board, at least two cells) that moves to a new place on every pass. Slip through the gap.
- **Computer player.** With nothing to chase it heads for the middle of the board, never steps into the void,
  and a pawn that always moves (a serpent, a runner) turns instead of trying to reverse.
- **Runner.** Landing locks your next hop and lane change for four ticks, so holding the hop button is no
  longer a way to stay in the air forever.
- **Examples.** Paddle Duel plays to 3 lives; Shrink Island is now a duel of two short serpents.

#### Added — linked multiplayer and leaderboards (Stage 4A, 2026-10-23)

- **Link Bus.** Join up to four cabinets with up to 16 Link Cables and a Link Bus to play one game together. The
  cabinet holding a cartridge runs the game; every other cabinet on the bus shows the same screen and seats one
  more player. Take a seat, press your action key when you are ready, and the countdown starts once everyone who
  sat down is ready. A second cartridge on the bus shows "LINKED — REMOVE CARTRIDGE"; a fifth cabinet shows
  "BUS FULL". Cables glow while a linked game is on.
- A Versus game with one player and a free cabinet waits ten seconds for a second player, then brings in the
  computer opponent. A Turns game is played at the cabinet holding the cartridge: pass the controls when the HUD
  says it is the next player's turn.
- Breaking the cabinet that runs the game, its Link Bus, or a cable mid-match ends the match as "ABANDONED" for
  everyone (nothing is recorded); breaking any other cabinet only frees its seat.
- **Marquee Screen.** On a bus it shows the game being played, with every player's score and the timer beside it,
  and rolls the cartridge's top ten between games. Without a bus it shows the NeroArcade card. Sneak-use still
  changes its size.
- **Leaderboards.** Every cartridge (and every copy of it) has a top-ten board and a personal best per player. The
  results card marks a new personal best. `/neroarcade top` shows the board of the cartridge in your hand or the
  cabinet you are looking at. A player who walks out of a match keeps a personal best but never takes a top-ten
  spot; abandoned and hot-seat matches record nothing.
- **Credit opt-in.** `/neroarcade privacy credit on|off` (off by default) decides whether your name appears on
  leaderboards and on games you save. Turning it off removes your name from every board on the server.
- New server settings: `multiplayer.enabled`, `leaderboards.enabled`, `privacy.leaderboardRetentionDays` (90,
  at most 365), `privacy.leaderboardMaxEntries` (10), plus the exchange and economy switches the next stage uses.

#### Added — module packs (2026-10-23)

- 36 new module chips, 43 in all, each crafted from a Blank Chip, its slot's reagent and one more item:
  - Arenas: **Grid 32** (A 32 by 32 board with a wall border and optional obstacles); **Ring 24** (A 24-wide tube that wraps left to right, with optional pillars); **Strip 9** (A 9-wide lane that can scroll new wall gates toward the pawn).
  - Pawns: **Paddle** (A slide-along bar that bounces hazards and shots back); **Runner** (Runs on its own; tap to hop over hazards, steer to change lane); **Serpent** (Always moving; grows with each pickup and dies touching itself); **Ship** (Drifts with inertia and fires shots where it points); **Stacker** (Guide falling pieces; full rows clear and score).
  - Rules: **Clear Board** (Collect every pickup before the clock runs out); **Last Standing** (Last pawn alive wins; alone, survive the solo clock); **Reach Exit** (Find the exit; with a key it stays locked until every key is held); **Score Target** (First to reach the target score wins).
  - Hazards: **Bouncing Ball** (A ball that bounces off walls and bats and scores past a goal line); **Chaser** (A drone that walks toward the nearest pawn through the maze); **Falling Blocks** (Crates drop from the top at random; dodge or catch them); **Laser Sweep** (A beam that sweeps the board back and forth; cross it when it passes); **Shrinking Floor** (The outer ring of the floor falls away at a steady interval).
  - Pickups: **Key** (Keys that unlock the exit; without one they are worth 5 points); **Score Orb** (Orbs worth points; they return unless the rule is to clear them); **Shield** (Grab it to shrug off the next hits for a while); **Speed Boost** (Grab it to move faster for a while).
  - Controls: **One button** (One action button and one alternate; the pawn moves on its own); **Twin stick** (Move with the pad, aim with the stick, fire with A); **Two-way** (Left and right (or up and down) plus two buttons).
  - Pace: **Pulse** (Alternates bursts of full speed with slow stretches); **Ramp** (Starts slow and speeds up to full speed in steps).
  - Modifiers: **Fog** (Only a circle around your pawn is visible); **Low Gravity** (Falling things fall slower; hops last longer); **Mirror** (Your controls are flipped on one or both axes); **Wrap-around** (The edges join up: leave one side, arrive on the other).
  - Multiplayer: **Co-op** (Two to four players share one board and, by default, one life pool); **Turns** (Players take turns at one cabinet; pass the controls when told); **Versus** (Players compete on one board; last one standing or best score wins).
  - Themes: **Cindara Dusk** (Ember reds and dusk amber under a warm sand glow); **Hangar** (Graphite deck, hazard yellow and field blue); **Terminal Green** (One-colour phosphor ladder with a single white hot spot).
- Sixteen example games, kept with the tests and the seed of the coming gallery (Paddle Duel, Serpent Garden, Stack Nine, Drone Maze, Lane Hopper,
  Hot-Seat Orbs, Tandem Sweep, Vault Run, Gunner Ring, Shrink Island, Mirror Pulse, Float Lane, Four-Way Rally,
  Crate Catch, Blind Tube, Stack Duel) that between them use every chip.
- In a hot-seat game a player waiting for their turn never reappears on the board between lives.
- A paddle's goal line never paints over an exit, a wall or a stacked block.
- Neon Void's description now says what it looks like.

#### Added — Stage 3 groundwork (2026-10-23)

- A new solid "block" cell for stacked pieces, drawn with its own tile; goal lines now draw as a dashed line in
  their seat's colour instead of looking like an exit. Every theme gained the stacker pawn sprite.
- A score rule no longer warns "nothing scores" when a hazard or pawn chip is what scores.
- The computer opponent now stops exactly on the orb it is after instead of hopping back and forth past it.

#### Fixed — Stage 2 review (2026-10-23)

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

#### Changed — Stage 2 review (2026-10-23)

- Rack rows show a short effect with live values ("Survive 60 s · lives 1", "Moves 4 ways · speed 3"); the full
  description is on hover. The rack is 24 px wider.
- The HUD's amber line is the rule itself ("Survive 60 s · lives 1"), and "ESC leave" stays under the stats for
  the whole match. The legend says "2nd action" instead of "alt"; the Four-way chip no longer repeats the buttons.
- A bench test play names your actual keys while it counts down; TEST PLAY's hover lists them.
- Steady's "Divisor" is now "Slow-down". Neon Void's Variant setting is gone until the theme variants exist.

#### Added — Stage 2: the core loop, client side (2026-10-21)

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

#### Added — Stage 2: the core loop, server side (2026-10-21)

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

#### Added — Stage 1: module contract and deterministic runtime (2026-10-20)

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
- `runtime.*` budget config keys and `screenRenderRange`, all server-authoritative. (Two cabinet power keys added
  here were removed again before release: there is no energy input yet.)
- Lang for every slot, default chip, cartridge error and bench warning.
- Tests: contract, codec, validator, schema, ids, RNG vectors, state hash, replay determinism with pinned
  hashes, snapshots, budgets, tick order, odd combinations, prediction and no-floats checks.

#### Changed — Stage 1 review fixes (2026-10-20)

- Hits are decided by the pawn and applied by the runtime: `PawnModule.onHit` returns `HitOutcome`
  (DAMAGE / DEFLECT / IGNORE); hazards gain `onDeflected`, `onShot` and `onGoal`; a damaging projectile is
  spent; pawn-vs-pawn hits are directional (head into body). New collision steps: goal cells
  (`Cell.GOAL_0..3`) and projectiles vs hazards. `ArenaModule.tick` runs after the pace each simulated tick.
  The runtime opponent is now driven by the pawn chip (`opponentInput`, default `RuntimeOpponent.greedy`)
  instead of a random walk, and the headless harness no longer scripts or logs its seat.
  `MatchState.solidAt` lets pawns treat `F_SOLID` entities as blocking. The pinned replay hashes are
  unchanged: no example cartridge uses a runtime opponent, projectiles, goal cells or solid entities.

#### Added — scaffold (2026-10-20)

- Multiloader skeleton for NeoForge, Forge and Fabric on Minecraft 26.1.2, 26.2 and 26.3 (nine cells),
  built on Neroland Core 1.15.0 with the Core floor derived from the compiled version.
- Numbered common init, platform seams (`PlatformInfo`, `NetworkPlatform`), the `neroarcade:main` network
  channel (no payloads yet), the NeroArcade creative tab (empty) and `config/neroarcade.properties`.
- Per-player data-erasure registration with Core (no player data is stored yet).
- Opt-out, anonymous crash telemetry wiring; inert until a Sentry project DSN is configured.
- Logo, CI, publish and wiki workflows.
