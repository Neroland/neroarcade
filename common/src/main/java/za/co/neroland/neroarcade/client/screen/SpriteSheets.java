package za.co.neroland.neroarcade.client.screen;

/**
 * One theme's four indexed sprite sheets (004 §6): 8x8 sprites left to right in chip registration order. Each
 * pixel is a palette index 0..15 (0 transparent). Built from ARGB pixels once at load (the client reads the PNGs
 * through the resource manager, tests through ImageIO) — never through the block atlas. Pure Java.
 */
public final class SpriteSheets {

    public static final int SPRITE = 8;

    /** The four per-theme sheets and their widths in pixels. */
    public enum Sheet {
        PAWNS("pawns", 80),
        HAZARDS("hazards", 80),
        PICKUPS("pickups", 32),
        TILES("tiles", 48);

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
     * or wrongly sized sheet stays transparent.
     */
    public static SpriteSheets fromArgb(ThemePalettes.Theme theme, int[][] argb) {
        SpriteSheets out = new SpriteSheets(theme);
        for (Sheet sheet : Sheet.values()) {
            int[] source = argb == null || sheet.ordinal() >= argb.length ? null : argb[sheet.ordinal()];
            byte[] target = out.pixels[sheet.ordinal()];
            if (source == null || source.length != target.length) {
                continue;
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

    /** True when the sheet has at least one opaque pixel in {@code sprite}. */
    public boolean has(Sheet sheet, int sprite) {
        if (sprite < 0 || sprite >= sheet.sprites()) {
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

    /** Palette index at {@code (x, y)} (0..7) of a sprite; 0 outside the sheet. */
    public int index(Sheet sheet, int sprite, int x, int y) {
        if (sprite < 0 || sprite >= sheet.sprites() || x < 0 || y < 0 || x >= SPRITE || y >= SPRITE) {
            return 0;
        }
        return pixels[sheet.ordinal()][y * sheet.width + sprite * SPRITE + x] & 15;
    }
}
