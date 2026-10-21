package za.co.neroland.neroarcade.client.screen;

/**
 * Turns the 64x64 index buffer into the colours the GPU sees (004 §5 "2x pixel look"): each logical pixel becomes
 * 2x2 texels of a 128x128 texture (sampled nearest-neighbour), and with the client-local scanline option every odd
 * texel row is darkened to 0.78 (200/256). Index 0 shows the background colour: a screen has no holes. Pure.
 */
public final class ScreenPixels {

    public static final int TEXTURE = ScreenRenderer.SIZE * 2;
    /** Scanline brightness, 200/256 = 0.78. */
    public static final int SCANLINE_SCALE = 200;

    private ScreenPixels() {
    }

    /** One texel, {@code 0xAARRGGBB}. */
    public static int texel(byte[] indices, ThemePalettes.Theme theme, boolean scanlines, int tx, int ty) {
        int index = indices[(ty >> 1) * ScreenRenderer.SIZE + (tx >> 1)] & 15;
        int argb = theme.colour(index == 0 ? ThemePalettes.BACKGROUND : index);
        if (scanlines && (ty & 1) == 1) {
            int r = (((argb >> 16) & 0xFF) * SCANLINE_SCALE) >> 8;
            int g = (((argb >> 8) & 0xFF) * SCANLINE_SCALE) >> 8;
            int b = ((argb & 0xFF) * SCANLINE_SCALE) >> 8;
            argb = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return argb;
    }

    /** Fills {@code out} (128x128, row-major ARGB). */
    public static void expand(byte[] indices, ThemePalettes.Theme theme, boolean scanlines, int[] out) {
        for (int ty = 0; ty < TEXTURE; ty++) {
            for (int tx = 0; tx < TEXTURE; tx++) {
                out[ty * TEXTURE + tx] = texel(indices, theme, scanlines, tx, ty);
            }
        }
    }
}
