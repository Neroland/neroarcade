# Using NeroLink

NeroArcade registers a module with Neroland Core's link API (`za.co.neroland.nerolandcore.link`), which the
NeroLink bridge serves to the NeroLink companion app. The module depends only on Core; NeroLink itself is optional.
It is registered as the **last** step of `NeroArcadeCommon.init()` (step 8) and swallows its own failures
(`RuntimeException | LinkageError`, one warning), so a missing or older Core link API never takes the mod down.
`link.enabled=false` (restart) registers nothing.

Versions: the module compiles against the link API of Neroland Core 1.15.0. Any NeroLink bridge that serves Core's
link registry can serve it; rich notification text needs NeroLink 1.1.0 or newer (see Events). The module's schema
version is 1.

Code: `common/src/main/java/za/co/neroland/neroarcade/link/`.

| Class | Role |
|---|---|
| `NeroArcadeLinkModule` | module id `neroarcade`, schema 1, five sections, two actions; registers the `"link"` eraser first |
| `ArcadeSnapshotProvider` | the sections (requester-scoped) |
| `ArcadeActionHandler` | the actions (server thread, rate-limited, idempotent by `requestId`) |
| `ArcadeEvents` | topic `neroarcade.events`, notification windows, the daily download digest, the `events` ring |
| `ArcadePushCopy` | the only builder of notification and alert text and of cabinet labels |
| `ArcadeLinkInfo`, `WikiLibrary` | discovery entry; the bundled wiki pages (WIKI CONTRACT v1) |
| `BoardKeys` | the one place a cartridge becomes the leaderboard store's board key |

## Boards are keyed by cartridge id

A leaderboard belongs to a **cartridge id**: copies and downloads keep the id and share its board, a remix gets a
new id and a fresh board. Everything the module sends uses that id as the board key: `leaderboard?cartridge=<id>`,
the `id` of every event, the notification window, the alert id `neroarcade.<id>.record` and the deep link
`nerolink://server/<serverId>/arcade/<id>`. `lineageRoot` is still sent everywhere, for information.

## Sections — `GET /api/v1/neroarcade/<section>`

Every object carries `asOf` (epoch milliseconds). Row and board times (`best.at`, `top[].at`) are epoch seconds;
event times are milliseconds. Strings that reach a phone are cleaned again (control and format characters removed).

- **`library`** (`?cursor=<id>&limit=<n>`, at most `link.libraryPageSize` = 50 per page, 12 KiB per page): what the
  server knows about you — your drafts, your published listings, and boards where you hold a personal best.
  `creditOptIn`, `actions.{export_cartridge,import_cartridge}` (the action switch and `exchange.enabled`: this is how
  the app learns an action is off), `limits.{draftsMax,draftsUsed,importPerHour}`, `cartridges[]` (`id`,
  `lineageRoot`, `title`, `genreTags`, `revision`, `state` = `draft` | `published` | `best_only`, `authored`,
  `plays`/`hearts` except on `best_only`, `best.{score,rank?,at?}`), `nextCursor`. The server cannot see
  inventories, so an unpublished cartridge in a chest is not listed.
- **`leaderboard?cartridge=<id>`**: `id`, `lineageRoot`, `title`, `retentionDays`, `enabled`, `top[]`
  (`rank`, `name` = the opted-in name or `null`, `score`, `at`; your own row has no `name`), `you`
  (`rank?`, `score`, `at?`, `inTop`) or `null`. An unknown id answers the same shape as an empty board. A malformed
  id answers `{"error":"bad cartridge"}`; more than 30 calls a minute `{"error":"rate limited"}`. No UUIDs anywhere.
- **`cabinets`**: only the cabinet you are seated at right now (empty otherwise): `label`
  (`<dimension> · Solo cabinet` or `<dimension> · Linked hall (N cabinets)`), `role` (`host` | `seat`), `seat`,
  `phase`, `multiplayer`, `seats`, `cartridge.{id,lineageRoot,title}` or `null`. Never a coordinate.
- **`events`** (`?limit=20`): your last 20 events, newest first, `{topic, at, payload}`. Memory only; serves the
  WebSocket on-subscribe snapshot of `neroarcade.events`.
- **`wiki`**: WIKI CONTRACT v1 — the pages of the in-repo `wiki/` folder, bundled with a generated `index.json`.

## Actions — `POST /api/v1/actions/neroarcade/<action>`

Order: `link.enabled` / `link.actions.<name>` / `exchange.enabled` → `ACTION_DISABLED`; a `requestId` over 64
characters → `VALIDATION`; a replayed `requestId` (10 minutes) → the first result, nothing applied twice; the rate
limit → `VALIDATION "rate limited, retry in <s>s"` (refused calls are not counted); then the action. Neither action
works offline (the bridge answers 409). Results are the bridge's `data` object.

- **`export_cartridge`** `{requestId, id, lineageRoot?}` — **by `id`**, which is required (a published remix
  shares its original's lineage root, and only the id tells them apart; without `id` → `VALIDATION "bad cartridge"`,
  and `lineageRoot` is ignored): your own draft with that `id`, else the published listing of that `id` (public:
  anyone may export it); otherwise `NOT_OWNER` — never a fallback to another cartridge of the same lineage.
  Result `{id, lineageRoot, title, revision, bytes, gameDef}`. `gameDef` is the cartridge's canonical JSON with
  `authorUuid` = your id only when you are the author, otherwise the anonymous marker
  `00000000-0000-4000-8000-00000000dead`; credit only as the listing already shows it. At most 12 KiB. 20 a minute,
  200 an hour.
- **`import_cartridge`** `{requestId, gameDef}` — at most 12 KiB measured on the canonical re-serialisation; the
  same `CartridgeValidator` as every in-game path (unknown module ids refused); the title must already be clean and
  pass the server's chat filter (while a slow filter has not decided: `VALIDATION "title pending review; try
  again"`, nothing added and not remembered for the `requestId`). You become the author, credit is cleared, `createdAt` is now; an id you did not author
  on this server gets a fresh id (a remix on the same lineage): only an id that is your own draft or your own
  listing is kept. The draft lands only in **your** Exchange drafts
  (`privacy.draftsPerPlayer`, 8 → `"drafts full"`). Result `{draftId, lineageRoot, title, draftsUsed, draftsMax}`.
  3 a minute and `link.importPerHour` (10) an hour.

There is no `challenge`, publish, unpublish, heart, seat or match-start action.

## Events — topic `neroarcade.events`

Every event is player-scoped (never a broadcast) and its payload has `type`, `at`, `id`, `lineageRoot`, `title`:

| `type` | Extra fields | Notification |
|---|---|---|
| `RECORD_BEATEN` | `newRank` (null = off the top 10) | "Record beaten" — "Your record on <title> was beaten — now #7"; at most one per board per `link.push.recordCooldownMinutes` (60). Falls inside the window are kept in the `events` section only; when the window closes the newest rank goes out once. Alert `neroarcade.<id>.record`. Never who or where. |
| `MATCH_RESULT` | `label`, `score`, `rank`, `seats`, `outcome`, `newBest`, `enteredTop` | none — kept in the `events` section only |
| `CARTRIDGE_DOWNLOADED` | `count`, `more?` | "Cartridge downloaded" — "<title> was downloaded 4 times today"; daily at `link.digestHour` (18, server-local), at most five per author |

Events that must not notify are kept off the link bus because NeroLink 1.x turns every player-scoped event without
`_notify` into a generic notification. Rich notification text needs NeroLink 1.1.0+ (`NotifyCopy`); with 1.0.0 the
phone shows the generic "NeroLink — neroarcade / events update". Phone notifications only reach players who turned
the **Arcade** category on in the app (off by default). With `pushNotifications.enabled=false` NeroArcade
publishes nothing to the bridge: every event is kept in the `events` section only (the app reads it on open and
every 30 s). The stored *alert* still exists, and a NeroLink bridge may announce it with a generic "alerts update"
notification that names nothing — a NeroLink release that honours `_push` suppresses that too.

## Config (`config/neroarcade.properties`, all server-authoritative)

| Key | Default | Range |
|---|---|---|
| `link.enabled` | true | (restart) |
| `link.actions.export_cartridge` | true | |
| `link.actions.import_cartridge` | true | |
| `link.importPerHour` | 10 | 1..100 |
| `link.push.recordCooldownMinutes` | 60 | 5..1440 |
| `link.digestHour` | 18 | 0..23 |
| `link.libraryPageSize` | 50 | 10..50 |
| `pushNotifications.enabled` | true | kill switch |

## Rules for changes

- Sections are scoped to the requesting player: never a roster, never `getNearestPlayer`, never a coordinate.
- Every value exposed must already be covered by [PRIVACY.md](PRIVACY.md) and by the erasure hook (store `"link"`).
- Notification and alert text comes only from `ArcadePushCopy`, whose methods take no player, UUID, name, position
  or level.
- The bridge needs no change for this module.
