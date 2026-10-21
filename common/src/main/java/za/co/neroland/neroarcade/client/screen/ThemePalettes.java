package za.co.neroland.neroarcade.client.screen;

import java.util.Locale;

/**
 * The four screen palettes of 004 §5, sixteen fixed roles each ({@code 0xFFRRGGBB}; index 0 is transparent).
 * A THEME chip's {@code ThemeSpec} names a palette and a sprite set by id; {@link #themeOf} maps either id to one
 * of the four sheets/palettes the mod ships (unknown ids fall back to {@code neon_void}, the default theme).
 * Pure Java.
 */
public final class ThemePalettes {

    public static final int TRANSPARENT = 0;
    public static final int BACKGROUND = 1;
    public static final int FLOOR = 2;
    public static final int WALL = 3;
    public static final int WALL_LIGHT = 4;
    public static final int EXIT = 5;
    public static final int PAWN = 6;
    public static final int PAWN_SHADE = 7;
    public static final int HAZARD = 8;
    public static final int HAZARD_HOT = 9;
    public static final int SIGNATURE = 10;
    public static final int PICKUP_2 = 11;
    public static final int TEXT = 12;
    public static final int TEXT_DIM = 13;
    public static final int FLASH = 14;
    public static final int DANGER = 15;

    /** The shipped themes, in sheet-folder names. */
    public enum Theme {
        NEON_VOID(0x05070D, 0x0B0F1A, 0x2A3140, 0x3A4458, 0x2FE3FF, 0xFF3FA4, 0xB0206E, 0xFF5C5C, 0xFF9A3C,
                0xFF3FA4, 0x58E07A, 0xE8ECF4, 0x8A93A6, 0xFFFFFF, 0xFF2020),
        HANGAR(0x1A1D22, 0x262B30, 0x5B6670, 0x8E98A4, 0xFFCE2C, 0x7FD7FF, 0x3FA9F5, 0xE04545, 0xFF8A8A,
                0x7FD7FF, 0x55D55A, 0xE8ECF4, 0x8E98A4, 0xFFFFFF, 0xFF2020),
        CINDARA_DUSK(0x1C0A14, 0x2A1020, 0x6B2A2A, 0xA04030, 0xFFD27A, 0xFFB347, 0xC66A1A, 0xFF5C3C, 0xFF9A1E,
                0xFF7A3C, 0xFFE08A, 0xFFE8D0, 0xB08A7A, 0xFFF4E0, 0xFF2A2A),
        TERMINAL_GREEN(0x031006, 0x06200C, 0x0F5A1F, 0x1F8A33, 0x9DFFB0, 0x5FE36A, 0x2FA83C, 0xE0FF5C, 0xFFFFFF,
                0x58E07A, 0xC8FF6A, 0x9DFFB0, 0x3F9A4F, 0xE8FFE8, 0xFF4040);

        private final int[] argb = new int[16];

        Theme(int... rgb) {
            argb[0] = 0;
            for (int i = 0; i < rgb.length; i++) {
                argb[i + 1] = 0xFF000000 | rgb[i];
            }
        }

        /** The 16 colours, {@code 0xAARRGGBB}; index 0 is fully transparent. */
        public int colour(int index) {
            return argb[index & 15];
        }

        /** A copy of the palette. */
        public int[] palette() {
            return argb.clone();
        }

        /** {@code neon_void}: the folder under {@code textures/screen/}. */
        public String folder() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private ThemePalettes() {
    }

    /**
     * The theme an id path names: {@code sprites/neon_void}, {@code palette/hangar_2}, {@code theme/cindara_dusk}
     * all resolve by the first shipped theme name they contain; anything else is {@link Theme#NEON_VOID}.
     */
    public static Theme themeOf(String idPath) {
        if (idPath != null) {
            String path = idPath.toLowerCase(Locale.ROOT);
            for (Theme theme : Theme.values()) {
                if (path.contains(theme.folder())) {
                    return theme;
                }
            }
        }
        return Theme.NEON_VOID;
    }

    /**
     * The palette index whose colour is {@code argb} (exact RGB match, first wins), transparent for alpha below
     * 128, otherwise the nearest colour by squared RGB distance. Used once per sheet at load.
     */
    public static int indexOf(Theme theme, int argb) {
        if ((argb >>> 24) < 128) {
            return TRANSPARENT;
        }
        int rgb = argb & 0xFFFFFF;
        int best = 1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 1; i < 16; i++) {
            int c = theme.colour(i) & 0xFFFFFF;
            if (c == rgb) {
                return i;
            }
            int dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            int dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            int db = (c & 0xFF) - (rgb & 0xFF);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }
}
