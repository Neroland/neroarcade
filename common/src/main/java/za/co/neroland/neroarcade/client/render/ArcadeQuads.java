package za.co.neroland.neroarcade.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * Quad helpers shared by the NeroArcade renderers. Coordinates are model pixels on a block authored facing north
 * (front face at z = 0, a north face is seen with +x on the viewer's left) and turned to the block's facing by
 * {@code q} quarter turns about the block centre. Every helper returns the vertices it emitted, which is what
 * {@link ArcadeBerStats#vertices} counts. CLIENT-ONLY.
 */
public final class ArcadeQuads {

    /**
     * A texture with a solid white patch (the chip band, rows 2..4, x 3..12): solid-colour quads sample its centre
     * and carry their colour in the vertex, so no extra texture ships for flat glows.
     */
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID,
            "textures/item/chip_band.png");
    public static final float[] WHITE_UV = {5f / 16f, 2.75f / 16f, 10f / 16f, 3.25f / 16f};
    /** The pixel font, loaded as a standalone texture (never the block atlas). */
    public static final Identifier FONT = Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID,
            "textures/screen/font/arcade5x7.png");

    private ArcadeQuads() {
    }

    /** Rotates the model-pixel point {@code (x, z)} about the block centre by {@code q} quarter turns. */
    public static float[] turn(float x, float z, int q) {
        return ArcadeCabinetRenderer.turn(x, z, q);
    }

    /** A direction turned by {@code q} quarter turns. */
    public static float[] turnVector(float x, float y, float z, int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {-z, y, x};
            case 2 -> new float[] {-x, y, -z};
            case 3 -> new float[] {z, y, -x};
            default -> new float[] {x, y, z};
        };
    }

    /** One point in model pixels → block units after the turn. */
    public static float[] point(float x, float y, float z, int q) {
        float[] t = turn(x, z, q);
        return new float[] {t[0] / 16f, y / 16f, t[1] / 16f};
    }

    public static void vertex(VertexConsumer c, PoseStack.Pose pose, float[] p, float u, float v, int argb,
            int light, float[] n) {
        c.addVertex(pose, p[0], p[1], p[2])
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, n[0], n[1], n[2]);
    }

    /**
     * A quad in the vertical plane {@code z} (model px, authored on a north-facing block): x from {@code x0} (the
     * viewer's left) to {@code x1}, y from {@code y0} (bottom) to {@code y1}; uv {@code {u0, v0, u1, v1}}. One-sided
     * (seen from the front) unless {@code twoSided}.
     *
     * @return vertices emitted
     */
    public static int wall(VertexConsumer c, PoseStack.Pose pose, int q, float z, float x0, float x1, float y0,
            float y1, float[] uv, int argb, int light, boolean twoSided) {
        float[][] corners = {point(x0, y1, z, q), point(x1, y1, z, q), point(x1, y0, z, q), point(x0, y0, z, q)};
        float[] tex = {uv[0], uv[1], uv[2], uv[1], uv[2], uv[3], uv[0], uv[3]};
        float[] n = turnVector(0f, 0f, -1f, q);
        // counter-clockwise as seen from the front (top-left, bottom-left, bottom-right, top-right)
        for (int i = 3; i >= 0; i--) {
            int k = (i + 1) % 4;
            vertex(c, pose, corners[k], tex[k * 2], tex[k * 2 + 1], argb, light, n);
        }
        if (!twoSided) {
            return 4;
        }
        float[] back = {-n[0], -n[1], -n[2]};
        for (int i = 0; i < 4; i++) {
            vertex(c, pose, corners[i], tex[i * 2], tex[i * 2 + 1], argb, light, back);
        }
        return 8;
    }

    /**
     * A horizontal quad at height {@code y} (model px): the square {@code half} px either side of {@code (cx, cz)},
     * turned {@code degrees} about its centre, then by the block facing. Two-sided (top and bottom).
     *
     * @return vertices emitted
     */
    public static int spinningFloor(VertexConsumer c, PoseStack.Pose pose, int q, float cx, float y, float cz,
            float half, float degrees, float[] uv, int argb, int light) {
        double a = Math.toRadians(degrees);
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        float[][] local = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
        float[][] corners = new float[4][];
        for (int i = 0; i < 4; i++) {
            float x = cx + local[i][0] * cos - local[i][1] * sin;
            float z = cz + local[i][0] * sin + local[i][1] * cos;
            corners[i] = point(x, y, z, q);
        }
        float[] tex = {uv[0], uv[1], uv[2], uv[1], uv[2], uv[3], uv[0], uv[3]};
        float[] up = {0f, 1f, 0f};
        float[] down = {0f, -1f, 0f};
        for (int i = 3; i >= 0; i--) {
            vertex(c, pose, corners[i], tex[i * 2], tex[i * 2 + 1], argb, light, up);
        }
        for (int i = 0; i < 4; i++) {
            vertex(c, pose, corners[i], tex[i * 2], tex[i * 2 + 1], argb, light, down);
        }
        return 8;
    }

    /**
     * A line of pixel-font text in the vertical plane {@code z}, centred on {@code cx}, glyph cells {@code h} px tall
     * with the top at {@code top}; one quad per visible glyph, one-sided.
     *
     * @return vertices emitted
     */
    public static int text(VertexConsumer c, PoseStack.Pose pose, int q, float z, float cx, float top, float h,
            String text, int argb, int light) {
        float unit = h / 7f;
        float advance = 6f * unit;
        float width = text.length() * advance - unit;
        float left = cx + width / 2f; // the viewer's left is +x on a north face
        int v = 0;
        for (int i = 0; i < text.length(); i++) {
            float[] uv = RenderPlan.glyphUv(text.charAt(i));
            if (uv == null) {
                continue;
            }
            float x0 = left - i * advance;
            v += wall(c, pose, q, z, x0, x0 - 5f * unit, top - h, top, uv, argb, light, false);
        }
        return v;
    }

    /** {@code 0xRRGGBB} → opaque ARGB. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
