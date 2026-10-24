# Gallery

The gallery is a showcase hall that shows everything NeroArcade adds, running on its own. It is meant for a test
world: screenshots, videos, trying the mod out. It replaces blocks where it is built.

## Building it

Stand on flat ground in a creative world and run:

```text
/neroarcade gallery
```

You need operator rights (permission level 2) and creative mode, and nobody else may be within 96 blocks of you.
The hall is built to the north of where you stand (the direction of negative Z), with its floor at the height of
your feet (one step up from the ground you stand on), about 15 blocks wide and 29 blocks deep. It contains:

- **One Arcade Cabinet per module chip** (43 of them), in two rows facing a centre aisle. Each cabinet holds one of
  the sixteen example games that uses that chip, and a sign in front of it names the chip in its slot colour and the
  game it is showing.
- **Every screen plays a recorded demo** of its game in attract mode — title card, the game being played, then the
  high scores — so the hall is alive without anyone sitting down. The demos are recordings that ship with the mod;
  each cabinet's screen is drawn on your own computer.
- **A workshop dais** with a Game Bench, a Cartridge Duplicator and an Arcade Exchange. The Exchange lists the sixteen
  example games with the credit "Gallery", so you can download and try them. Gallery listings do not count towards
  quests.
- **A link row**: four linked cabinets on a Link Bus under a Marquee Screen (scale 3). The marquee shows the link
  row's four-player demo mid-match with its score strip.

The light comes from the cabinets and hidden light blocks under the ceiling.

The build stops with a message, and changes nothing, when:

- you are not in creative mode, or are not an operator;
- someone else is within 96 blocks (the gallery replaces blocks, so it is never built where another player is);
- the space holds any block with contents — a chest, a furnace, a machine — that an earlier gallery did not place,
  so it can never delete an inventory.

Building again while standing inside a gallery replaces that gallery.

## Clearing it

Stand anywhere inside the hall and run:

```text
/neroarcade gallery clear
```

The clear puts back exactly the blocks the build changed, as they were before. A block someone changed since the
build is left alone. The gallery's cartridges disappear (they are never dropped), its Exchange listings are taken
down, and loose items within 64 blocks are removed. Take your own items out of the gallery's Game Bench, Cartridge
Duplicator and Exchange tray first: whatever is left in them is removed with the gallery. The gallery remembers
what it placed in the world's save, so a clear still works after a restart.

## Screenshots

`/nsarcgallery capture [time]` (a client command) takes the gallery's screenshots for you:

1. It stops the day cycle and the weather (`advance_time` and `advance_weather` off), sets the time (`day`, `noon`,
   `night` or `midnight`; `night` by default) and clears the weather.
2. It builds the gallery where you stand, waits for every screen to start its demo, and removes dropped items within
   64 blocks.
3. It moves the camera through a fixed list of shots with the HUD hidden and saves each one to
   `screenshots/neroarcade/` in your game folder. The first, `hero.png`, looks down the aisle from 12 blocks back and
   3 up.
4. During the hero shot it also writes the renderer numbers to `screenshots/neroarcade/budget.txt`.

If anyone else is in the world, the capture says so: their name and skin could appear in a frame, so check every
screenshot before you publish it. The screenshots stay on your computer.

`/nsarcber` prints how much drawing NeroArcade's screens and machines cost over the last 200 frames (vertices and
milliseconds per frame, live screens and running demos); `/nsarcber reset` starts a new count.

## Servers

The gallery works on a dedicated server for an operator in creative mode. Server software that mixes the mod loader
with plugin platforms (Paper-style hybrids) is not supported — by the gallery or by the rest of NeroArcade.
