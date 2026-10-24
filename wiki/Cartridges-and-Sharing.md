# Cartridges and Sharing

A **Cartridge** holds one game: its title, its chips and their settings, an id, a revision number, and a list of the
games it was remixed from (its *lineage*). It never holds code, so a cartridge from someone else can only ever be a
combination of chips the server already has. NeroArcade has three ways to share a game — **save**, **copy**, and
**publish/download** — and remixing is a save.

## Save

At a [Game Bench](Getting-Started.md), put a **Blank Cartridge** in the tray, type a title and press **SAVE TO
CARTRIDGE**. Each save onto a blank makes a new game with a new id.

- **Re-saving your own game.** Put the cartridge you saved back in the tray and SAVE again: the same cartridge is
  updated, keeps its id (and its leaderboard) and its revision goes up by one.
- **LOAD** puts a cartridge's chips into the rack so you can change them. Loaded chips have an amber dashed frame
  and cannot be taken out of the bench (they are copies of the cartridge, not items); **✕ CLEAR** removes them.
- The bench refuses, with a sentence that says what to do, if you try to save over someone else's cartridge or over
  a different game.

## Copy — the Cartridge Duplicator

| Item | Recipe |
| --- | --- |
| **Cartridge Duplicator** | Nero Alloy, copper ingot, Nero Alloy on top; copper ingot, redstone, copper ingot in the middle; three iron ingots below. |

Put a cartridge and a Blank Cartridge in and press **Copy**. Three seconds later you have the original back and an
identical copy: the same game, the same id, the same credit — and so the same leaderboard. The platter spins while
it copies and the front bar fills. A copy can be made of anyone's cartridge; it is a copy, not a new game.

## Publish and download — the Arcade Exchange

| Item | Recipe |
| --- | --- |
| **Arcade Exchange** | Iron ingot, glass pane, iron ingot; iron ingot, emerald, iron ingot; iron ingot, Nero Alloy, iron ingot. |

The Exchange ("Find a game to download") lists every game published on the server, most hearted first, eight to a
page. Each listing shows the title, genre words in their slot colours, the ten slots (hover a slot to see its
chips, or "Empty — the game uses the default"), hearts and plays, and the builder's name only if they turned credit
on ("Anonymous builder" otherwise).

- **Publish**: put your cartridge in the tray (or hold it) and press Publish. The title goes through the server's
  chat filter. You can publish only games you built. Publishing the same game again replaces its listing (hearts
  and plays are kept).
- **Unpublish**: take your own listing down. Operators can take any listing down, and
  `/neroarcade admin unpublish <player>` removes everything one player listed.
- **Download**: put a Blank Cartridge in the tray and press Download. The blank becomes the listed game, unchanged
  — same id, same credit, same leaderboard.
- **♥ Heart** a game you like: once per game per session, up to 20 an hour, never your own.
- **My drafts**: games you sent from the NeroLink app wait here, for you only (up to 8, kept 30 days). **Fetch to
  cartridge** writes one onto a Blank.

Limits: at most 200 listings on a server and 16 per player (operators exempt). A full Exchange refuses new listings
rather than deleting anyone's game. A listing whose chips this server does not have shows in red and cannot be
downloaded. The tray is yours alone: two players at one kiosk never see each other's cartridges, and the tray comes
back to you when you close the screen.

**Plays** counts a copy's first finished match in a cabinet. It is a signal, not a score: taking a cartridge out and
putting it back in can count again.

## Remix

LOAD someone else's cartridge into your bench, change what you like, put a Blank in the tray and SAVE. The result is
**your** game with a new id and its own leaderboard; it remembers the original in its lineage, and its tooltip says
"Remixed from another game". You are its builder.

## Credit: showing your name

By default nobody's name is shown anywhere: games say "by Anonymous builder" and boards say "Anonymous". To be named:

```text
/neroarcade privacy credit on
```

From then on, games you save carry your Minecraft name and your new leaderboard rows show it. `/neroarcade privacy
credit off` turns it off again and removes your name from every leaderboard, Exchange listing and draft on the
server, and from any Game Bench that has your game loaded. `/neroarcade privacy credit` on its own tells you which
it is. Cartridges that are already items (in chests or other players' inventories) keep the credit they were saved
with: the server cannot change items. See [Privacy](Privacy.md).

## Cartridges from other servers

The NeroLink app can export a game as a file and import one into your drafts (see [NeroLink](NeroLink.md)). An
imported game becomes yours: you are its builder, its credit is cleared, and unless it is one of your own games on
this server it gets a new id and its own leaderboard. Every cartridge is checked the same way wherever it comes
from: unknown chips, settings out of range, or a game over 8 KiB are refused with the reason.
