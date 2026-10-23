package za.co.neroland.neroarcade.client.screen;

/**
 * One theme's indexed sprite sheets (004 §6): 8x8 sprites left to right in chip registration order. Each
 * pixel is a palette index 0..15 (0 transparent). Built from ARGB pixels once at load (the client reads the PNGs
 * through the resource manager, tests through ImageIO) — never through the block atlas. Pure Java.
 *
 * <p>The base sheets keep their 0.1.0 widths; roles added later live in <b>extension sheets</b> ({@code *_ext})
 * and are addressed as if the base sheet continued: {@code index(PAWNS, 10, ..)} reads {@code PAWNS_EXT}
 * sprite 0, {@code index(TILES, TILE_BLOCK, ..)} reads {@code TILES_EXT} sprite 0 (004 "Sprite roles added
 * 2026-10-23").
 */
public final class SpriteSheets {

    public static final int SPRITE = 8;

    /** The per-theme sheets and their widths in pixels. Append only (ext sheets after the base four). */
    public enum Sheet {
        PAWNS("pawns", 80),
        HAZARDS("hazards", 80),
        PICKUPS("pickups", 32),
        TILES("tiles", 48),
        /** Pawn pairs 5.. (stacker = pair 5 → ext pair 0). */
        PAWNS_EXT("pawns_ext", 16),
        /** Tiles 6.. (block = tile 6 → ext x 0, goal = tile 7 → ext x 8). */
        TILES_EXT("tiles_ext", 16);

        public final String file;
        public final int width;

        Sheet(String file, int width) {
            this.file = file;
            this.width = width;
        }

        public int sprites() {
            return width / SPRITE;
        }
    }

    // tiles.png order
    public static final int TILE_FLOOR = 0;
    public static final int TILE_FLOOR_ALT = 1;
    public static final int TILE_WALL = 2;
    public static final int TILE_WALL_TOP = 3;
    public static final int TILE_EXIT = 4;
    public static final int TILE_VOID = 5;
    // tiles_ext.png, addressed through TILES
    /** {@code Cell.BLOCK}: palette 6 is remapped to the seat colour. */
    public static final int TILE_BLOCK = 6;
    /** {@code Cell.GOAL_0..3}: palette 5 is remapped to the goal seat's colour. */
    public static final int TILE_GOAL = 7;

    private final ThemePalettes.Theme theme;
    private final byte[][] pixels = new byte[Sheet.values().length][];

    private SpriteSheets(ThemePalettes.Theme theme) {
        this.theme = theme;
        for (Sheet sheet : Sheet.values()) {
            pixels[sheet.ordinal()] = new byte[sheet.width * SPRITE];
        }
    }

    /** All-transparent sheets: what a theme draws as when its textures are missing (tiles fall back to fills). */
    public static SpriteSheets blank(ThemePalettes.Theme theme) {
        return new SpriteSheets(theme);
    }

    /**
     * Sheets from ARGB pixels, {@code argb[sheet.ordinal()]} row-major with the sheet's width and height 8. A missing
     * ({@code null}) sheet stays transparent; the client's loader turns a missing or wrongly sized file into
     * {@code null} after logging it.
     *
     * @throws IllegalArgumentException if a sheet is present but wrongly sized — never dropped silently, so a sheet
     *                                  whose width drifted from {@link Sheet#width} fails its test
     */
    public static SpriteSheets fromArgb(ThemePalettes.Theme theme, int[][] argb) {
        SpriteSheets out = new SpriteSheets(theme);
        for (Sheet sheet : Sheet.values()) {
            int[] source = argb == null || sheet.ordinal() >= argb.length ? null : argb[sheet.ordinal()];
            byte[] target = out.pixels[sheet.ordinal()];
            if (source == null) {
                continue;
            }
            if (source.length != target.length) {
                throw new IllegalArgumentException(theme + " " + sheet.file + " has " + source.length
                        + " pixels, expected " + target.length + " (" + sheet.width + "x" + SPRITE + ")");
            }
            for (int i = 0; i < target.length; i++) {
                target[i] = (byte) ThemePalettes.indexOf(theme, source[i]);
            }
        }
        return out;
    }

    public ThemePalettes.Theme theme() {
        return theme;
    }

    /** The extension sheet that continues {@code sheet}, or null. */
    public static Sheet extension(Sheet sheet) {
        return switch (sheet) {
            case PAWNS -> Sheet.PAWNS_EXT;
            case TILES -> Sheet.TILES_EXT;
            default -> null;
        };
    }

    /** True when the sheet has at least one opaque pixel in {@code sprite}. */
    public boolean has(Sheet sheet, int sprite) {
        if (sprite < 0) {
            return false;
        }
        for (int y = 0; y < SPRITE; y++) {
            for (int x = 0; x < SPRITE; x++) {
                if (index(sheet, sprite, x, y) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Palette index at {@code (x, y)} (0..7) of a sprite; 0 outside the sheet. A sprite past the end of a base sheet
     * continues into its extension sheet.
     */
    public int index(Sheet sheet, int sprite, int x, int y) {
        if (sprite >= sheet.sprites()) {
            Sheet ext = extension(sheet);
            if (ext != null) {
                return index(ext, sprite - sheet.sprites(), x, y);
            }
        }
        if (sprite < 0 || sprite >= sheet.sprites() || x < 0 || y < 0 || x >= SPRITE || y >= SPRITE) {
            return 0;
        }
        return pixels[sheet.ordinal()][y * sheet.width + sprite * SPRITE + x] & 15;
    }
}
