package za.co.neroland.neroarcade.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.block.Half;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.client.ArcadeClientConfig;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.match.ClientMatches;

/**
 * The cabinet's screen: one full-bright quad on the upper half's tilted screen face (model element {@code screen},
 * x 3..13, y 2..12, z 1, tilted 22.5° back about x at [8, 2, 1]), textured with the cabinet's dynamic screen
 * texture from {@link CabinetScreens}. Drawn from the lower half's block entity.
 *
 * <p><b>Culling — never blanket true</b> (the BufferBuilder-overflow precedent): only within the screen render
 * range ({@code screenRenderRange}, or this computer's shorter {@code client.screenRenderRange}) of the screen's
 * centre, and only when the camera is in front of the screen; NeoForge also frustum-tests
 * {@link #getRenderBoundingBox} (both halves). A cabinet without a texture slot submits nothing and the static
 * model's ink face shows; an asleep cabinet holding a readable cartridge still draws its client-local attract loop.
 * Within {@link RenderPlan#TEXT_RANGE} blocks it also writes the marquee title (the cartridge title, upper case, at
 * most twelve characters, or {@code NEROARCADE}) in the pixel font on the marquee glow strip, one quad per glyph.
 * Every frame is counted in {@link ArcadeBerStats}. CLIENT-ONLY.
 */
public class ArcadeCabinetRenderer implements BlockEntityRenderer<ArcadeCabinetBlockEntity, ArcadeCabinetRenderState> {

    /** Screen centre relative to the lower block's origin, in blocks (for the range test). */
    private static final double CENTRE_Y = 1.0 + 7.0 / 16.0;
    private static final float TILT = (float) Math.toRadians(22.5);
    /** Proud of the ink face along its normal, in model pixels. */
    private static final float LIFT = 0.05f;
    private static final RenderType FONT_TYPE = RenderTypes.entityTranslucentEmissive(ArcadeQuads.FONT);
    /** The marquee glow strip on the upper half: x 2..14, y 13..15, front at z = -0.5 (model px). */
    private static final float MARQUEE_Z = -0.6f;
    private static final float MARQUEE_CY = 16f + 14f;
    private static final float MARQUEE_W = 11f;
    private static final RenderType[] TYPES = new RenderType[TextureLru.DEFAULT_CAP + 1];
    private static final Identifier[] TYPE_IDS = new Identifier[TextureLru.DEFAULT_CAP + 1];

    @Override
    public ArcadeCabinetRenderState createRenderState() {
        return new ArcadeCabinetRenderState();
    }

    @Override
    public int getViewDistance() {
        return ArcadeClientConfig.screenRenderRange();
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(ArcadeCabinetBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }

    @Override
    public boolean shouldRender(ArcadeCabinetBlockEntity be, Vec3 cameraPos) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.HALF) || state.getValue(ArcadeBlockProperties.HALF) != Half.LOWER) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + CENTRE_Y;
        double cz = pos.getZ() + 0.5;
        double range = getViewDistance();
        double dx = cameraPos.x - cx;
        double dy = cameraPos.y - cy;
        double dz = cameraPos.z - cz;
        if (dx * dx + dy * dy + dz * dz > range * range) {
            ArcadeBerStats.culled();
            return false;
        }
        Direction facing = state.getValue(ArcadeBlockProperties.FACING);
        // In front of the screen: the camera is on the facing side of the cabinet's front plane.
        boolean front = dx * facing.getStepX() + dz * facing.getStepZ() > -0.25;
        if (!front) {
            ArcadeBerStats.culled();
        }
        return front;
    }

    @Override
    public void extractRenderState(ArcadeCabinetBlockEntity be, ArcadeCabinetRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        ArcadeBerStats.frame(be.getLevel() == null ? 0L : be.getLevel().getGameTime(), partialTick);
        long t0 = ArcadeBerStats.start();
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(ArcadeBlockProperties.FACING) ? state.getValue(ArcadeBlockProperties.FACING)
                : Direction.NORTH;
        s.quarterTurns = quarterTurns(facing);
        s.screen = CabinetScreens.prepare(be, ClientMatches.now());
        BlockPos pos = be.getBlockPos();
        boolean near = RenderPlan.inRange(cameraPos.x - pos.getX() - 0.5, cameraPos.y - pos.getY() - 1.875,
                cameraPos.z - pos.getZ() - 0.5, Math.min(RenderPlan.TEXT_RANGE, getViewDistance()));
        s.marquee = near ? RenderPlan.marqueeText(CabinetScreens.shownCabinet(be).clientInfo().title()) : null;
        if (s.screen == null && s.marquee == null) {
            ArcadeBerStats.culled();
        }
        ArcadeBerStats.stop(t0);
    }

    @Override
    public void submit(ArcadeCabinetRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        Identifier screen = s.screen;
        String marquee = s.marquee;
        if (screen == null && marquee == null) {
            return;
        }
        long t0 = ArcadeBerStats.start();
        ArcadeBerStats.submitted();
        int q = s.quarterTurns;
        if (screen != null) {
            collector.order(1).submitCustomGeometry(poseStack, renderType(screen), (pose, c) -> screenQuad(c, pose, q));
            ArcadeBerStats.vertices(RenderPlan.QUAD_2SIDED);
        }
        if (marquee != null) {
            float h = Math.min(2f, MARQUEE_W * 7f / Math.max(1, 6 * marquee.length() - 1));
            collector.order(2).submitCustomGeometry(poseStack, FONT_TYPE, (pose, c) -> ArcadeQuads.text(c, pose, q,
                    MARQUEE_Z, 8f, MARQUEE_CY + h / 2f, h, marquee, ArcadeQuads.opaque(ArcadeColours.TEXT_LIGHT),
                    ArcadeColours.FULL_BRIGHT));
            ArcadeBerStats.vertices(RenderPlan.glyphQuads(marquee) * RenderPlan.QUAD);
        }
        ArcadeBerStats.stop(t0);
    }

    static RenderType renderType(Identifier id) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPE_IDS[i] == null) {
                TYPE_IDS[i] = id;
                TYPES[i] = RenderTypes.entityTranslucentEmissive(id);
                return TYPES[i];
            }
            if (TYPE_IDS[i].equals(id)) {
                return TYPES[i];
            }
        }
        return RenderTypes.entityTranslucentEmissive(id);
    }

    /** Quarter turns clockwise (seen from above) of a horizontal facing: N 0, E 1, S 2, W 3. */
    public static int quarterTurns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /**
     * The tilted screen quad in the lower block's space: authored facing north on the upper half (+16 px), rotated
     * 22.5° about x at [8, 18, 1], lifted along its normal, then turned to the cabinet's facing. Double-sided.
     */
    static void screenQuad(VertexConsumer c, PoseStack.Pose pose, int q) {
        screenQuad(c, pose, q, 1f);
    }

    /** As above for a screen element whose front and tilt origin sit at {@code originZ} (the Exchange: 2). */
    static void screenQuad(VertexConsumer c, PoseStack.Pose pose, int q, float originZ) {
        // viewer's top-left, top-right, bottom-right, bottom-left (a north face is seen with +x on the left)
        float[][] corners = {
            corner(13f, 12f, q, originZ), corner(3f, 12f, q, originZ), corner(3f, 2f, q, originZ),
            corner(13f, 2f, q, originZ)
        };
        float[] uv = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
        float cos = (float) Math.cos(TILT);
        float sin = (float) Math.sin(TILT);
        float[] n = turnVector(0f, sin, -cos, q); // the face normal after the tilt: up and toward the front
        for (int i = 0; i < 4; i++) {
            vertex(c, pose, corners[i], uv[i * 2], uv[i * 2 + 1], n[0], n[1], n[2]);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(c, pose, corners[i], uv[i * 2], uv[i * 2 + 1], -n[0], -n[1], -n[2]);
        }
    }

    /** One authored corner (model px on the upper half) → block units in the lower block's space. */
    private static float[] corner(float x, float y, int q, float originZ) {
        float cos = (float) Math.cos(TILT);
        float sin = (float) Math.sin(TILT);
        float dy = y - 2f;
        float dz = -LIFT;
        float ry = 2f + dy * cos - dz * sin;
        float rz = originZ + dy * sin + dz * cos;
        float[] turned = turn(x, rz, q);
        return new float[] {turned[0] / 16f, (ry + 16f) / 16f, turned[1] / 16f};
    }

    /** Rotates the model-pixel point {@code (x, z)} about the block centre by {@code q} quarter turns. */
    static float[] turn(float x, float z, int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {16f - z, x};
            case 2 -> new float[] {16f - x, 16f - z};
            case 3 -> new float[] {z, 16f - x};
            default -> new float[] {x, z};
        };
    }

    private static float[] turnVector(float x, float y, float z, int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {-z, y, x};
            case 2 -> new float[] {-x, y, -z};
            case 3 -> new float[] {z, y, -x};
            default -> new float[] {x, y, z};
        };
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float[] p, float u, float v, float nx, float ny,
            float nz) {
        c.addVertex(pose, p[0], p[1], p[2])
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(ArcadeColours.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
