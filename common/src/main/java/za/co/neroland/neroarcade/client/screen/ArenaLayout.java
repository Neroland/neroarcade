package za.co.neroland.neroarcade.client.screen;

/**
 * Where an arena of {@code w x h} cells sits on the 64x64 logical screen (004 §5): integer pixels per cell, never
 * sub-pixel, centred with the rest letterboxed in {@code palette[1]}. Square-ish arenas scale uniformly (32 cells
 * = 2 px, 16 cells = 4 px); a strip ({@code strip_9}: one side at most 9 cells, the other at least 16) gets
 * {@code 64 / short} px across and 2 px along, whatever its length (9 x 32 = 63 x 64, 9 x 16 = 63 x 32 letterboxed).
 * Pure.
 *
 * @param sx pixels per cell horizontally
 * @param sy pixels per cell vertically
 * @param ox left margin in pixels
 * @param oy top margin in pixels
 */
public record ArenaLayout(int width, int height, int sx, int sy, int ox, int oy) {

    public static final int SCREEN = 64;
    /** A strip's short side is at most this many cells... */
    public static final int STRIP_SHORT = 9;
    /** ...and its long side at least this many. */
    public static final int STRIP_LONG = 16;
    /** Pixels per cell along a strip. */
    public static final int STRIP_ALONG = 2;

    public static ArenaLayout of(int width, int height) {
        int w = Math.max(1, Math.min(SCREEN, width));
        int h = Math.max(1, Math.min(SCREEN, height));
        int sx;
        int sy;
        if (w <= STRIP_SHORT && h >= STRIP_LONG) {
            sx = SCREEN / w;
            sy = STRIP_ALONG;
        } else if (h <= STRIP_SHORT && w >= STRIP_LONG) {
            sx = STRIP_ALONG;
            sy = SCREEN / h;
        } else {
            int s = Math.max(1, Math.min(SCREEN / w, SCREEN / h));
            sx = s;
            sy = s;
        }
        return new ArenaLayout(w, h, sx, sy, (SCREEN - w * sx) / 2, (SCREEN - h * sy) / 2);
    }

    /** Screen x of a fixed-point position (256 units per cell). */
    public int px(int xUnits) {
        return ox + ((xUnits * sx) >> 8);
    }

    /** Screen y of a fixed-point position. */
    public int py(int yUnits) {
        return oy + ((yUnits * sy) >> 8);
    }
}
