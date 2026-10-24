# Modules: Rule

The **Rule** slot decides how a match is won or lost. One chip. Empty means **Survive** (60 seconds, one life).
Ingredient: **paper**. [All slots](Modules.md)

| Chip | What it does | HUD goal line | Recipe (shapeless) |
| --- | --- | --- | --- |
| **Survive** (default) | Stay alive until the clock runs out. | Survive 60 s · lives 1 | Blank Chip + paper |
| **Clear Board** | Collect every pickup before the clock runs out. | Clear in 120 s · lives 3 | Blank Chip + paper + bone meal |
| **Last Standing** | Last pawn alive wins; alone, survive the solo clock. | Last alive · lives 1 | Blank Chip + paper + bone |
| **Reach Exit** | Find the exit; with a key it stays locked until every key is held. | Reach the exit · lives 3 | Blank Chip + paper + any wooden door |
| **Score Target** | First to reach the target score wins. | Reach 100 pts · lives 3 | Blank Chip + paper + emerald |

The HUD line shows the chip's current values. Every match also has a hard time limit set by the server
(`runtime.maxMatchSeconds`, 10 minutes by default); a match still running then ends in a draw.

## Survive

Win when the clock reaches zero with a pawn still alive; lose when every life is gone.

| Setting | Default | Range |
| --- | --- | --- |
| Seconds | 60 | 10–600 |
| Lives | 1 | 1–9 |

## Clear Board

Win by collecting every **clearable** pickup (Score Orbs and Keys) before the clock runs out. In this game those
pickups do not come back once taken. Without a clearable pickup the game plays as Survive with this chip's seconds.

| Setting | Default | Range |
| --- | --- | --- |
| Lives | 3 | 1–9 |
| Seconds | 120 | 10–600 |

## Last Standing

The last pawn alive wins; if everyone goes down together it is a draw. Played alone — or left alone when the others
leave — it becomes a survival game on the solo clock.

| Setting | Default | Range |
| --- | --- | --- |
| Lives | 1 | 1–9 |
| Solo seconds | 60 | 10–600 |

## Reach Exit

An exit appears on the far side of the board from where you start. Step on it to win. With **Needs key** on, the
exit stays locked until every Key pickup on the board has been collected.

| Setting | Default | Range |
| --- | --- | --- |
| Needs key | off | on, off |
| Lives | 3 | 1–9 |

## Score Target

The first player to reach the target score wins. There is no clock of its own (only the server's time limit).
Points come from pickups, from a Bouncing Ball scoring past a goal line, from shooting Chaser drones, and from the
Stacker's rows.

| Setting | Default | Range |
| --- | --- | --- |
| Target | 100 | 1–9999 |
| Lives | 3 | 1–9 |

## Odd combinations

- Reach Exit with a pawn that moves on one line puts the exit at the far end of that line (the bench warns). With a
  Paddle it cannot be reached.
- Reach Exit with keys and Shrinking Floor: a key that falls into a hole can never be collected, so the exit never
  opens and the match runs to the time limit.
- Clear Board with Shrinking Floor: a pickup left in a hole cannot be collected; the clock decides.
- Score Target with nothing that scores runs to the time limit (the bench warns).
