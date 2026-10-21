package za.co.neroland.neroarcade.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mod's own 5x7 bitmap font ({@code textures/screen/font/arcade5x7.png}, 80x28, 16 glyphs per row, ASCII
 * 32..95, white on transparent, no gutter; 004 Registry ids). Upper case only: lower case is folded, anything
 * else draws as {@code ?}. Glyphs are 5 wide with a 1-pixel gap, so a 64-pixel screen fits 10 characters a line.
 * Pure Java.
 */
public final class ArcadeFont {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    public static final int ADVANCE = GLYPH_W + 1;
    public static final int SHEET_W = 80;
    public static final int SHEET_H = 28;
    /** Characters per 64-pixel line. */
    public static final int LINE_CHARS = 10;

    private final boolean[] lit = new boolean[SHEET_W * SHEET_H];

    private ArcadeFont() {
    }

    /** A font that draws nothing (textures missing). */
    public static ArcadeFont blank() {
        return new ArcadeFont();
    }

    /** From the sheet's ARGB pixels (80x28); alpha of at least 128 is lit. */
    public static ArcadeFont fromArgb(int[] argb) {
        ArcadeFont font = new ArcadeFont();
        if (argb != null && argb.length == font.lit.length) {
            for (int i = 0; i < argb.length; i++) {
                font.lit[i] = (argb[i] >>> 24) >= 128;
            }
        }
        return font;
    }

    /** The drawable character for {@code c}: upper-cased, {@code ?} outside ASCII 32..95. */
    public static char normalise(char c) {
        char upper = Character.toUpperCase(c);
        return upper >= 32 && upper <= 95 ? upper : '?';
    }

    public boolean lit(char c, int x, int y) {
        if (x < 0 || y < 0 || x >= GLYPH_W || y >= GLYPH_H) {
            return false;
        }
        int code = normalise(c) - 32;
        int gx = (code % 16) * GLYPH_W + x;
        int gy = (code / 16) * GLYPH_H + y;
        return lit[gy * SHEET_W + gx];
    }

    /** Pixel width of a line. */
    public static int width(String text) {
        return text.isEmpty() ? 0 : text.length() * ADVANCE - 1;
    }

    /**
     * Upper-cases and word-wraps {@code text} into at most {@code maxLines} lines of at most {@code maxChars}
     * characters; a word longer than a line is cut. Never returns an empty list for non-blank text.
     */
    public static List<String> wrap(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxChars <= 0 || maxLines <= 0) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().toUpperCase(Locale.ROOT).split("\\s+")) {
            String w = word.length() > maxChars ? word.substring(0, maxChars) : word;
            if (line.length() == 0) {
                line.append(w);
            } else if (line.length() + 1 + w.length() <= maxChars) {
                line.append(' ').append(w);
            } else {
                lines.add(line.toString());
                if (lines.size() == maxLines) {
                    return lines;
                }
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0 && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        return lines;
    }
}
