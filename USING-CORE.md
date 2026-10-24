# Using Neroland Core

NeroArcade hard-depends on **Neroland Core** and on nothing else. NeroEconomy is an optional compile-time
dependency (see the economy flag below); NeroLink and NeroQuests are reached only through Core's APIs and data.

## Version pin and floor

`nerolandcore_version` in `gradle.properties` (currently **1.15.0**) is the Core version NeroArcade compiles
against. Every manifest derives its floor from it, so the loader refuses a Core older than the one NeroArcade was
built against:

- NeoForge / Forge: `versionRange = "[${nerolandcoreVersion},2.0)"`
- Fabric: `"nerolandcore": ">=${nerolandcore_version} <2.0.0"`

Pin a **tagged** Core release, never a working-tree version.

## Resolving Core

Core is consumed per cell as `za.co.neroland.nerolandcore:nerolandcore-<loader>-<mc>:<version>`, resolved
from:

1. `mavenLocal()` — run Core's `publishToMavenLocal` for local development, or
2. GitHub Packages (`NerolandCoreGitHubPackages` in `stonecutter.gradle`) — needs `gpr.user` / `gpr.key`
   Gradle properties, or `GITHUB_ACTOR` / `GITHUB_TOKEN` in the environment (CI has both).

## Core APIs NeroArcade uses

| Core API | Used for |
|----------|----------|
| `config.ConfigSchema` / `ConfigManager` / `ConfigValue` | `config/neroarcade.properties`: every key, its range and whether it is server-authoritative (synced to clients on join) or client-local |
| `registry.RegistrationProvider` | Cross-loader registration of blocks, items, block entities, menus, sounds, data components and the creative tab |
| `registry.BlockCodecs` | Block codecs for the seven blocks |
| `data.PlayerDataErasure` / `PlayerDataEraser` | The single composite eraser in `data/NeroArcadeData` (leaderboards, credit, exchange, drafts, benches, exports, seats, link) |
| `data.SavedDataRecovery` | Every `SavedData` read (`neroarcade:leaderboards`, `neroarcade:credit`, `neroarcade:exchange`, the gallery store) and the backup refresh after each erasure step |
| `progression.ProgressionGates` / `GateScope` | Player milestones `neroarcade:first_cartridge`, `first_published`, `first_versus_win` |
| `event.ThresholdEvents` | World channels `neroarcade:match_won`, `cartridge_published`, `record_set` (scope = a cartridge id or the Exchange, never a player) |
| `event.CoreEvents` | The quest hook `neroarcade:hook/blank_cartridges` |
| `palette.PaletteRegistry` / `Finish` | The theme colours, registered with Core's palette through `compat/palette` |
| `link.*` (`NeroLinkRegistry`, `LinkSnapshotProvider`, `LinkActionHandler`, `LinkEvent`, `LinkAlert`, `LinkAlerts`, `LinkModuleInfo`, `LinkActionResult`) | The `neroarcade` companion-app module — see [USING-NEROLINK.md](USING-NEROLINK.md) |
| `nerolandcore:materials/nero_alloy` item tag | Recipes for the cabinet, bench, duplicator, exchange, Link Bus, Blank Cartridge and the pawn chips |
| `neroland:highlight/*` item tags | Slot-border highlights for NeroArcade's machines, cartridges, chips and blanks |

NeroArcade adds nothing to Core's creative tab and nothing to Core's network channel; it has its own tab
(`neroarcade`) and its own channel (`neroarcade:main`).

## Erasure

`data/NeroArcadeData` registers one eraser with `PlayerDataErasure` during common init. One erasure request
(`/neroland data eraseme`, or an operator's `/neroland data erase <uuid>`) walks every NeroArcade store and then
refreshes each store's recovery backup. `ArcadeErasureConformanceTest` runs Core's `ErasureConformance` harness over
the real erasers; `PlayerKeyedStateInventoryTest` fails on any new player-keyed store that has no eraser. See
[PRIVACY.md](PRIVACY.md) for what each store holds.

## The economy flag

`neroeconomy_compat=true` (the default) compiles `compat/economy/bridge/NeroEconomyCoinOp` against NeroEconomy
`neroeconomy_version` (0.4.0-beta.1); it is the only class that imports NeroEconomy and is found through
`META-INF/services` only after `isModLoaded("neroeconomy")`. `-Pneroeconomy_compat=false` builds without the
NeroEconomy artifact. Both configurations must build.
