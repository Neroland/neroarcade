# NeroArcade

Build, play and share mini games inside Minecraft. Part of the Neroland mod family, built on
**Neroland Core**.

> **Status:** scaffold (0.0.1-alpha.1). The mod loads and registers its own creative tab, config and
> network channel, but has no gameplay yet.

## Supported platforms

| Loader | Minecraft |
|--------|-----------|
| NeoForge | 26.1.2, 26.2, 26.3 |
| Forge | 26.1.2, 26.2, 26.3 |
| Fabric | 26.1.2, 26.2, 26.3 |

Requires **Neroland Core** (see `gradle.properties` for the pinned version; the loader refuses an older Core).

## Building from source

Java 25 and the bundled Gradle wrapper. Neroland Core resolves from `mavenLocal()` or GitHub Packages (see
[USING-CORE.md](USING-CORE.md)). Build the nine cells explicitly, never a plain `build`:

```text
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build
```

Jars land in `<loader>/versions/<mc>/build/libs/`.

## Documentation

- [CHANGELOG.md](CHANGELOG.md)
- [PRIVACY.md](PRIVACY.md)
- [USING-CORE.md](USING-CORE.md) — how NeroArcade consumes Neroland Core
- [USING-NEROLINK.md](USING-NEROLINK.md) — companion-app integration (planned)
- [AGENTS.md](AGENTS.md) — layout and conventions for contributors

## License

All Rights Reserved; modpacks are allowed. See [LICENSE](LICENSE).
