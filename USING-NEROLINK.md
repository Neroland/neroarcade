# Using NeroLink

**Planned — not implemented.** NeroArcade has no link module yet.

When one is added it will register with Neroland Core's link API (`za.co.neroland.nerolandcore.link`),
depend only on Core, and go last in `NeroArcadeCommon.init()` (step 8), swallowing its own failures so a
broken companion integration never takes the mod down.

Rules it must follow:

- Sections are scoped by what NeroArcade actually knows about the requesting player: owner UUID where a
  record has one, proximity to the online player where it does not. Never a server-wide roster.
- Never use `getNearestPlayer` for authorisation.
- Every value exposed must already be covered by [PRIVACY.md](PRIVACY.md) and by the erasure hook.
