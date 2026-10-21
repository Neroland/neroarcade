package za.co.neroland.neroarcade.client.screen;

import java.util.List;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.hook.ThemeSpec;

/**
 * Which sprite each entity of a game draws with: the PAWN chip picks the pawn sprite, each HAZARD/PICKUP stack
 * position its own sprite, the THEME chip the palette and sheets. Sheet order is the chip registration order of
 * 004 §6; a chip the sheets do not know draws with the first sprite of its sheet. Pure; built once per game.
 *
 * @param pawnSprite   index into {@code pawns.png} pairs (cursor 0, serpent 1, runner 2, paddle 3, ship 4)
 * @param hazardSprite per hazard stack position, index into {@code hazards.png} pairs
 * @param pickupSprite per pickup stack position, index into {@code pickups.png}
 */
public record SpriteMap(ThemePalettes.Theme theme, int pawnSprite, int[] hazardSprite, int[] pickupSprite) {

    static final List<String> PAWNS = List.of("cursor", "serpent", "runner", "paddle", "ship");
    static final List<String> HAZARDS = List.of("falling_block", "bouncing_ball", "chaser", "laser_sweep",
            "shrinking_floor");
    static final List<String> PICKUPS = List.of("score_orb", "speed_boost", "shield", "key");

    /** The serpent's body segment sprite (the only non-animation pair in pawns.png). */
    public static final int SERPENT = 1;

    /** Default theme, default sprites (no game known). */
    public static final SpriteMap DEFAULT = new SpriteMap(ThemePalettes.Theme.NEON_VOID, 0, new int[3], new int[3]);

    public SpriteMap {
        hazardSprite = hazardSprite.clone();
        pickupSprite = pickupSprite.clone();
    }

    public static SpriteMap of(ResolvedGame game) {
        if (game == null) {
            return DEFAULT;
        }
        ThemePalettes.Theme theme;
        try {
            ThemeSpec spec = game.theme().spec(game.themeProps());
            ThemePalettes.Theme bySprites = ThemePalettes.themeOf(spec.spriteSetId().getPath());
            theme = bySprites != ThemePalettes.Theme.NEON_VOID ? bySprites
                    : ThemePalettes.themeOf(spec.paletteId().getPath());
        } catch (RuntimeException e) {
            theme = ThemePalettes.Theme.NEON_VOID;
        }
        int[] hazards = new int[3];
        for (int i = 0; i < game.hazards().size() && i < 3; i++) {
            hazards[i] = indexOf(HAZARDS, game.hazards().get(i));
        }
        int[] pickups = new int[3];
        for (int i = 0; i < game.pickups().size() && i < 3; i++) {
            pickups[i] = indexOf(PICKUPS, game.pickups().get(i));
        }
        return new SpriteMap(theme, indexOf(PAWNS, game.pawn()), hazards, pickups);
    }

    /** The hazard sprite for a stack tag (0..2). */
    public int hazard(int tag) {
        return hazardSprite[Math.floorMod(tag, 3)];
    }

    /** The pickup sprite for a stack tag (0..2). */
    public int pickup(int tag) {
        return pickupSprite[Math.floorMod(tag, 3)];
    }

    static int indexOf(List<String> order, ArcadeModule module) {
        String path = module.id().getPath();
        String name = path.substring(path.indexOf('/') + 1);
        int index = order.indexOf(name);
        return Math.max(0, index);
    }

    @Override
    public int[] hazardSprite() {
        return hazardSprite.clone();
    }

    @Override
    public int[] pickupSprite() {
        return pickupSprite.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SpriteMap that && theme == that.theme && pawnSprite == that.pawnSprite
                && java.util.Arrays.equals(hazardSprite, that.hazardSprite)
                && java.util.Arrays.equals(pickupSprite, that.pickupSprite);
    }

    @Override
    public int hashCode() {
        return theme.hashCode() * 31 + pawnSprite * 7 + java.util.Arrays.hashCode(hazardSprite)
                + java.util.Arrays.hashCode(pickupSprite) * 3;
    }
}
