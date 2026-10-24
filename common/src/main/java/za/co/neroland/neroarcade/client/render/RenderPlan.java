package za.co.neroland.neroarcade.client.render;

import java.util.Locale;

/**
 * The renderers' draw ranges and geometry counts as plain numbers, so the render budget is checked in a plain JVM
 * ({@code RenderBudgetTest}) and the renderers emit exactly what is counted here. Every renderer culls on its own
 * range (a sphere around what it draws, never a blanket {@code true}), on the camera being in front of its face, and
 * on NeoForge also on its render bounding box; the screen-bearing ones share {@link CabinetScreens}' texture pool.
 *
 * <table>
 *   <caption>Vertices per renderer per frame, at most</caption>
 *   <tr><th>Renderer</th><th>Near</th><th>Far (past the text range)</th></tr>
 *   <tr><td>Cabinet: tilted screen (2-sided) + marquee title (1 quad per glyph)</td><td>8 + 48</td><td>8</td></tr>
 *   <tr><td>Marquee Screen: square + score strip (2-sided)</td><td>16</td><td>16</td></tr>
 *   <tr><td>Game Bench, while editing: 10 chips + preview (2-sided)</td><td>88</td><td>0 beyond its range</td></tr>
 *   <tr><td>Duplicator, while copying: platter (2-sided) + progress bar</td><td>12</td><td>0 beyond its range</td></tr>
 *   <tr><td>Exchange: listing screen (2-sided) + "XCHG" wordmark (4 glyphs)</td><td>24</td><td>0 beyond the text range</td></tr>
 * </table>
 *
 * Pure.
 */
public final class RenderPlan {

    /** A double-sided quad. */
    public static final int QUAD_2SIDED = 8;
    /** A one-sided quad. */
    public static final int QUAD = 4;

    /** Pixel-font text (cabinet marquee title, exchange screen) is culled past this many blocks (004 §4.5). */
    public static final int TEXT_RANGE = 16;
    /** The bench holo-rack and the duplicator platter draw within this many blocks. */
    public static final int PROP_RANGE = 24;

    /** The longest cabinet marquee title (004 §4.1). */
    public static final int MARQUEE_CHARS = 12;
    /** The word a cabinet without a cartridge shows. */
    public static final String MARQUEE_IDLE = "NEROARCADE";

    /** Chip billboards on the holo-rack (one per slot kind). */
    public static final int RACK_CHIPS = 10;

    public static final int CABINET_MAX = QUAD_2SIDED + MARQUEE_CHARS * QUAD;
    public static final int MARQUEE_MAX = 2 * QUAD_2SIDED;
    public static final int BENCH_MAX = RACK_CHIPS * QUAD_2SIDED + QUAD_2SIDED;
    public static final int DUPLICATOR_MAX = QUAD_2SIDED + QUAD;
    public static final int EXCHANGE_MAX = QUAD_2SIDED + 4 * QUAD;

    private RenderPlan() {
    }

    /** The marquee text: upper case, the bitmap font's characters only, at most {@value #MARQUEE_CHARS}. */
    public static String marqueeText(String title) {
        String upper = title == null ? "" : title.strip().toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < upper.length() && out.length() < MARQUEE_CHARS; i++) {
            char c = upper.charAt(i);
            out.append(c >= 32 && c <= 95 ? c : ' ');
        }
        String text = out.toString().strip();
        return text.isEmpty() ? MARQUEE_IDLE : text;
    }

    /** Glyph quads the marquee text needs (spaces draw nothing). */
    public static int glyphQuads(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            n += text.charAt(i) == ' ' ? 0 : 1;
        }
        return n;
    }

    /** Cabinet vertices at a squared camera distance: the screen, plus the title when within the text range. */
    public static int cabinetVertices(double distanceSq, String title) {
        int v = QUAD_2SIDED;
        if (distanceSq <= (double) TEXT_RANGE * TEXT_RANGE) {
            v += glyphQuads(marqueeText(title)) * QUAD;
        }
        return v;
    }

    /** Within {@code range} blocks of a point, given the camera offset. */
    public static boolean inRange(double dx, double dy, double dz, double range) {
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    /**
     * The pixel-font glyph cell of {@code c} in {@code font/arcade5x7.png} (80×28, 16 per row, ASCII 32..95):
     * {@code {u0, v0, u1, v1}} in texture fractions, or null for a space or a character the font lacks.
     */
    public static float[] glyphUv(char c) {
        if (c <= 32 || c > 95) {
            return null;
        }
        int i = c - 32;
        float u0 = (i % 16) * 5f / 80f;
        float v0 = (i / 16) * 7f / 28f;
        return new float[] {u0, v0, u0 + 5f / 80f, v0 + 7f / 28f};
    }

    /** Holo-rack bob in model pixels: ±1 px on a 2 s sine (004 §4.2). */
    public static float bob(float ticks) {
        return (float) Math.sin(ticks * Math.PI * 2.0 / 40.0);
    }

    /** Duplicator platter angle in degrees: one turn every 40 ticks (004 §4.4). */
    public static float platterDegrees(float ticks) {
        return (ticks * 9f) % 360f;
    }
}
