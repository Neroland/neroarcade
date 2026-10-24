# Privacy

NeroArcade keeps as little about you as it can. This page says, in plain words, what a server keeps, how to choose
whether your name is shown, and how to get a copy of your data or have it erased. The full disclosure is the mod's
`PRIVACY.md`.

## What the server keeps about you

- **Games you save.** A cartridge remembers that you made it, by your player id. Nobody sees that id: tooltips,
  screens and other players' games never show it. Your **name** is on the game only if you turned credit on.
- **Scores.** Each game has a top-ten board. A row holds your score, when you set it, and your name only if credit
  was on (otherwise "Anonymous"). The server also keeps your personal best on each game, which only you see. Rows
  are deleted after 90 days (a server can choose a shorter time, never more than a year); a personal best is deleted
  a year after you set it, and beating it starts the year again.
- **The Arcade Exchange.** A game you publish is listed with its title, genre words, and two numbers: **plays** and
  **hearts**. Plays goes up when a copy of the game finishes its first match in a cabinet. It is a signal, not a
  score: it does not say who played, and taking a cartridge out and putting it back in can count again. Hearts is a
  count; who hearted what is remembered only until the server restarts, to stop double hearts.
- **Drafts** you import from the NeroLink app wait at the Exchange for you only, for up to 30 days.
- **Your seat** at a cabinet is remembered only while you sit there, never on disk.

The server does **not** keep a history of the games you played, your button presses after a match, your chat, or
where you were.

## Credit: showing your name (off by default)

```text
/neroarcade privacy credit on
/neroarcade privacy credit off
/neroarcade privacy credit
```

With credit **on**, games you save and your new scores show your Minecraft name. With credit **off** (the default)
they say "Anonymous builder" and "Anonymous". Turning it off removes your name from every board, listing and draft on
the server straight away. Games you already gave to other players keep what they carried: the server cannot change
items in someone else's inventory or chest.

## Get a copy of your data

```text
/neroarcade privacy export
```

The server writes one file with everything NeroArcade keeps about you (your scores and personal bests, your listings
and drafts, your credit setting) and tells you where it is in the world folder. Ask the server operator for it. It
never contains anyone else's data. You can ask once a minute; the file is deleted after 7 days.

## Erase your data

```text
/neroland data eraseme
```

This one command erases you from every Neroland mod on the server. For NeroArcade it deletes your scores and
personal bests, your credit setting, your drafts and your export file, takes every listing of your games off the
Exchange and replaces you with an anonymous marker on any draft of them another player still holds. Cartridges that are
already items in the world (in chests, other players' inventories, cabinets or a duplicator) keep what they carry,
because the server cannot reach items.

## Phone notifications

If you use the NeroLink app and turn the Arcade notifications on, a notification says only a game's title and your
new rank, or how many times your game was downloaded today. It never says who beat you or where. Notifications pass
through the NeroLink relay (`nerorelay.neroserver.xyz`) and Apple's or Google's push service on the way to your phone.

## Questions

Ask your server operator, or write to [info@neroland.co.za](mailto:info@neroland.co.za).
