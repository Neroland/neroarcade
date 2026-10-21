# Using Neroland Core

NeroArcade hard-depends on **Neroland Core** and on nothing else.

## Version pin and floor

`nerolandcore_version` in `gradle.properties` is the Core version NeroArcade compiles against. Every
manifest derives its floor from it, so the loader refuses a Core older than the one NeroArcade was built
against:

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
| `config.ConfigSchema` / `ConfigManager` | `config/neroarcade.properties` |
| `registry.RegistrationProvider` | Cross-loader registration (the creative tab so far) |
| `data.PlayerDataErasure` | The single composite eraser in `data/NeroArcadeData` |
| `data.SavedDataRecovery` | Every future `SavedData` read and post-erasure backup refresh |
