[![NeroLink App — Beta](https://img.shields.io/badge/NeroLink_App-Now_in_Beta-60d4e8?style=for-the-badge)](https://nerolandmc.net/nerolink/#beta) [![Explore the Neroland ecosystem](https://img.shields.io/badge/Explore-The_Neroland_Ecosystem-1a5a6c?style=for-the-badge)](https://nerolandmc.net/ecosystem/)

> 📱 **NeroLink App Beta — your Neroland world on your phone.** Check energy, alerts and machines live, claim quest rewards and search your storage without logging in. **[Join the beta at nerolandmc.net →](https://nerolandmc.net/nerolink/#beta)**
>
> 🌌 **Explore the Neroland ecosystem.** See how NeroArcade fits together with the rest of the Nero mods — every mod, wiki and changelog in one place. **[View the ecosystem at nerolandmc.net →](https://nerolandmc.net/ecosystem/)** · [NeroArcade on the website](https://nerolandmc.net/mods/neroarcade/)

---

# NeroArcade

**Build a mini game from chips, save it to a cartridge, and play it on an arcade cabinet your whole base can watch.**

NeroArcade is the **mini-game construction kit** of the Neroland ecosystem. A game is ten **module chips** — the board, what you steer, how you win, what threatens you, what you collect, the controls, the speed, one twist, how many play, and the look. Slot them into a **Game Bench**, test-play, save to a **Cartridge**, and slide it into an **Arcade Cabinet**. Swap one chip and you have a different game. Cartridges hold a game, not a program, so they are safe to copy, publish, download and remix — and cabinets joined by a **Link Bus** play versus and co-op on one shared match.

Built on **Neroland Core**, so it shares the ecosystem's config framework, materials, progression milestones and the one data-erasure hook every Nero mod answers to.

*(In development — first release 0.1.0-beta.1.)*

---

## What it adds

1. **Game Bench & module chips.** Ten fixed slots — Arena, Pawn, Rule, Hazard, Pickup, Control, Pace, Modifier, Multiplayer and Theme — one chip each, up to three in Hazard and Pickup. **43 chips** ship, each with typed settings (whole numbers, switches, short choices — never free text). The bench previews as you tweak, test-plays before you save, and flags odd-but-legal combinations with a friendly warning. Chips are crafted from Blank Chips and are never used up.
2. **Cartridges.** A saved game: title, chips, settings, an id, a revision and its remix lineage. Re-save your own cartridge to update it in place; every cartridge — from a bench, the Exchange or the companion app — passes the same fail-closed check, so unknown chips or out-of-range settings are refused with the reason, never silently dropped.
3. **Arcade Cabinets.** A 1 × 2 cabinet with a 64 × 64 screen that everyone within 32 blocks in front of it can watch, an attract loop that shows the high scores while idle, and a seated HUD. Matches run on the server at a fixed 20 Hz and are fully deterministic, so the same game, seed and inputs always replay the same way.
4. **Link Bus multiplayer.** Run Link Cables from a Link Bus to up to four cabinets and they play **one** game, hosted by the cabinet holding the cartridge — versus, co-op, or hot-seat turns at a single cabinet. A Versus game with one player brings in a computer opponent. A **Marquee Screen** on the bus shows the live match for spectators and rolls the top ten between games.
5. **Cartridge Duplicator & Arcade Exchange.** Copy any cartridge at the Duplicator. Publish, unpublish, download and heart games at the Exchange kiosk — every game published on the server, most hearted first. A remix is just a bench save of someone else's game: a new game with its own board that remembers the original.
6. **Leaderboards.** A top ten and personal bests per game, written only by the cabinet running the match — nothing a game client sends can post a score. Copies and downloads share a board; remixes start fresh. Check any board with `/neroarcade top`.
7. **Sixteen example games and a Gallery.** Serpent Garden, Vault Run, Paddle Duel and more ship as examples, and `/neroarcade gallery` builds a showcase hall of every chip running recorded demos.

## Privacy (POPIA / GDPR)

NeroArcade keeps as little as it can, and **never shows a player's name unless that player opts in**. A server stores your player UUID as the author of games you save (never displayed or sent to other players), your top-ten rows and personal bests (90 days and one year by default, never more than a year), the Exchange listings you publish, drafts you import from the companion app (30 days), and whether you turned credit on. Credit is **off by default** — `/neroarcade privacy credit on|off` — and turning it off removes your name from every board, listing and draft at once. There is **no play history**, no inputs kept after a match, no chat, no positions and no IP addresses. Get a copy with `/neroarcade privacy export`; erase everything with Neroland Core's `/neroland data eraseme`, which clears your NeroArcade data alongside every other Neroland mod. (Cartridges that are already items in chests or other players' inventories are beyond a server's reach — which is why credit is opt-in.)

> **Telemetry notice:** NeroArcade contains opt-out, anonymous crash reporting via Sentry (EU servers) — **on by default.** A report carries only the stack trace of an error in NeroArcade code, the mod/game/loader/Java versions and the list of loaded mod ids — never IPs, usernames, UUIDs, world data or chat. Opt out any time by setting `telemetryEnabled=false` in `config/neroarcade.properties`. Full details: [PRIVACY.md](https://github.com/Neroland/neroarcade/blob/main/PRIVACY.md).

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one config framework, shared materials (chips and blocks are crafted with Nero Alloy), Core progression milestones, and Core's shared per-player erasure hook. NeroArcade ships in its own creative tab.
- 📱 **NeroLink module** — browse your library, drafts and leaderboards in the companion app, export and import cartridges as files, and opt in to "Record beaten" and download-digest notifications that never say who beat you or where.
- 🤝 **Interoperates, never hard-depends** — **NeroEconomy** (optional coin-op seats and paid downloads, off by default), **NeroQuests** (an "Insert Coin" chapter shipped as data) and **NeroLink** are all optional and detected at start-up.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2**, **26.2** and **26.3**.

## Requirements & compatibility

- **Requires [Neroland Core](https://nerolandmc.net/mods/neroland-core/) 1.15.0 or newer** — install it alongside NeroArcade (it loads first).
- Optional: **NeroEconomy** 0.4.0-beta.1+, **NeroLink** 1.0.0+ (1.1.0 for rich notification text) and **NeroQuests**. NeroArcade is complete without them.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroArcade by Neroland* with links to this page and the [GitHub repository](https://github.com/Neroland/neroarcade). Full terms: [LICENSE](https://github.com/Neroland/neroarcade/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/neroarcade/wiki)** — getting started, every chip and its settings, worked example games, sharing, multiplayer, commands and every config key.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/neroarcade/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/neroarcade/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/neroarcade)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
