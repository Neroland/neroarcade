# Commands

NeroArcade's commands start with `/neroarcade`. The player commands need no operator permission; `gallery` and
`admin` need operator permission level 2. Replies never show a player id, and a leaderboard reply shows a name only
for a player who turned credit on.

## Players

| Command | What it does |
| --- | --- |
| `/neroarcade cartridge inspect` | Describes the cartridge in your main hand: title, id, revision, builder ("Anonymous builder" unless credited), lineage, and every slot's chips. An unreadable cartridge says why. |
| `/neroarcade top` | Shows the top ten of the cartridge in your hand or, with an empty hand, of the cabinet you are looking at (within 6 blocks), plus your personal best. |
| `/neroarcade privacy credit` | Says whether credit is on or off for you. |
| `/neroarcade privacy credit on` | Your name appears on games you save and on your new leaderboard rows. |
| `/neroarcade privacy credit off` | The default. Removes your name from every board, listing and draft on the server. |
| `/neroarcade privacy export` | Writes everything NeroArcade keeps about you to one file in the world folder and tells you its path (ask the server operator for it). Once a minute; the file is deleted after 7 days. |

## Operators

| Command | What it does |
| --- | --- |
| `/neroarcade gallery` | Builds the showcase hall north of you. Needs creative mode and nobody else within 96 blocks. See [Gallery](Gallery.md). |
| `/neroarcade gallery clear` | Puts back exactly the blocks the gallery changed (run it inside the hall). |
| `/neroarcade admin awake` | How many cabinets are running a game now, of the `runtime.maxAwakeCabinets` cap, and how many are waiting. Names no one. |
| `/neroarcade admin unseat <player>` | Frees an online player's seat. During a match they are treated like an erased seat: the match forgets them and records nothing for them. |
| `/neroarcade admin unpublish <player>` | Removes every Exchange listing that player published. Works for offline players. |

## Client commands

These run on your own game and need no permission on the server.

| Command | What it does |
| --- | --- |
| `/nsarcgallery capture [time]` | Builds the gallery where you stand and takes its screenshots to `screenshots/neroarcade/`. `time` is `day`, `noon`, `night` (default) or `midnight`. Needs the same permissions as `/neroarcade gallery`. |
| `/nsarcber` | Prints how much drawing NeroArcade's screens and machines cost over the last 200 frames. |
| `/nsarcber reset` | Starts a new count. |

## Neroland Core commands you will want

| Command | What it does |
| --- | --- |
| `/neroland data eraseme` | Erases your data from every Neroland mod on the server, NeroArcade included. See [Privacy](Privacy.md). |
| `/neroland data erase <uuid>` | Operators: erases one player's data, by their UUID. |
| `/neroland config list` | Lists every Neroland mod's settings. |
| `/neroland config reload` | Operators: reloads the config files and re-syncs them to players. |
