# Modules: Pickup

The **Pickup** slot decides what the pawns collect. Up to **three** chips (the same chip twice with different
settings is fine). Empty means no pickup. Ingredient: **gold nugget**. [All slots](Modules.md)

| Chip | What it does | Recipe (shapeless) |
| --- | --- | --- |
| **Key** | Keys that unlock the exit; without one they are worth 5 points. | Blank Chip + gold nugget + tripwire hook |
| **Score Orb** | Orbs worth points; they return unless the rule is to clear them. | Blank Chip + gold nugget + gold nugget |
| **Shield** | Grab it to shrug off the next hits for a while. | Blank Chip + gold nugget + iron ingot |
| **Speed Boost** | Grab it to move faster for a while. | Blank Chip + gold nugget + sugar |

Pickups are placed on free floor cells at random (from the match's seed), never right next to where a player starts.
Every pickup a Serpent collects makes it longer.

## Key

Placed once at the start, a few cells away from every player, and never replaced. With the Reach Exit rule and
**Needs key** on, the exit unlocks when every key has been collected. In any other game a key is simply worth
5 points (the bench warns). Keys count as clearable for Clear Board.

| Setting | Default | Range |
| --- | --- | --- |
| Count | 1 | 1–4 |

## Score Orb

Each orb gives its value to whoever collects it. A collected orb comes back somewhere else after one second —
except under Clear Board, where the orbs are placed once and the goal is to take them all.

| Setting | Default | Range |
| --- | --- | --- |
| Value | 10 | 1–100 points |
| Count | 3 | 1–32 orbs |

## Shield

Collecting it protects the pawn for the duration: hazards and shots do not cost a life. A collected shield returns
after five seconds.

| Setting | Default | Range |
| --- | --- | --- |
| Duration (ticks) | 100 | 20–400 |
| Count | 1 | 1–4 |

## Speed Boost

Collecting it makes the pawn faster for the duration. A collected boost returns after five seconds.

| Setting | Default | Range |
| --- | --- | --- |
| Duration (ticks) | 100 | 20–400 |
| Count | 1 | 1–4 |
