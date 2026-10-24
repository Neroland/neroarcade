# NeroLink

With the **NeroLink** mod on the server and the NeroLink companion app on your phone, NeroArcade shows up in the app
as **Arcade**. Everything there is about **you**: the app never lists other players, never shows where anyone is,
and never shows a player id. Without NeroLink on the server, NeroArcade works exactly the same, without the app.

## What you see

- **Library** — your drafts, the games you published, and every game where you hold a personal best, with your
  best score and rank. The server cannot see inside inventories, so a cartridge in a chest that was never published
  is not listed.
- **Leaderboards** — the top ten of a game (names only for players who turned credit on; your own row is marked as
  you), your best, and how long rows are kept. Boards are per cartridge id, exactly as in the game (see
  [Leaderboards](Leaderboards.md)).
- **Cabinet** — the cabinet you are sitting at right now, labelled like "Overworld · Linked hall (3 cabinets)",
  with the game and the match phase. Never a coordinate.
- **Events** — your last 20 arcade events: records beaten, match results, downloads of your games.
- **Wiki** — these pages.

## Export and import

- **Export** turns a game into a file you can share: any game published on this server, or one of your own drafts.
  Another builder's id is replaced by an anonymous marker, and their name is included only if they show credit at
  the Exchange.
- **Import** sends a game file to **your own** Exchange drafts on the server (pick it up at an Arcade Exchange under
  **My drafts**). The server checks it exactly like a cartridge made at a bench: a file over 12 KiB, a chip this
  server does not have, or a title the chat filter refuses is turned away with the reason. You become its builder
  and its credit is cleared. Unless the file is one of your own games on this server, it gets a new id and its own
  leaderboard — an imported file can never post onto someone else's board. Up to 8 drafts, kept 30 days; 3 imports
  a minute and 10 an hour.

The app cannot publish, unpublish, heart, take a seat or start a match; those happen in the game. There is no
"challenge a friend" in this version.

## Notifications

Arcade notifications are **off** until you turn the Arcade category on in the app's settings. Then:

- **Record beaten** — "Your record on Neon Dodge was beaten — now #2". It names the game and your new rank, never who
  beat you or where. At most one per game per hour; if your record falls again within the hour, the next
  notification carries the newest rank. Tapping it opens that game's board.
- **Cartridge downloaded** — once a day, "Neon Dodge was downloaded 4 times today", for your published games.

Match results appear in the app's events list, never as a notification. Notifications pass through the NeroLink
relay and Apple's or Google's push service on the way to your phone. A server owner can switch NeroArcade's
notifications off entirely (`pushNotifications.enabled=false`). With an older NeroLink bridge the text may be a
generic "events update".

## Privacy

Everything the app can see is already described in [Privacy](Privacy.md). An erasure request
(`/neroland data eraseme`) also clears your arcade events and alerts.
