package za.co.neroland.neroarcade.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.bus.MarqueeScreenBlockEntity;
import za.co.neroland.neroarcade.client.ArcadeClientConfig;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.match.ClientMatches;

/**
 * The Marquee Screen billboard (004 §4.6, 003 §5): a {@code scale} × {@code scale}·⅔ surface whose bottom centre
 * sits on the block's panel, drawn just in front of the panel's north face (z = 10 px). While its bus host plays,
 * the left two-thirds show the host's own screen texture (shared with the host cabinet, never redrawn) and the
 * right third the score strip; while the host is idle the square rolls the cartridge's top ten; with no bus the
 * whole surface shows the static {@code marquee_idle.png} card.
 *
 * <p><b>Culling — never blanket true:</b> only within the screen render range of the surface's centre and only
 * when the camera is in front of it; NeoForge also frustum-tests {@link #getRenderBoundingBox}, which covers the
 * scaled surface. Textures come from the shared {@link CabinetScreens} pool (its LRU cap counts marquees).
 * CLIENT-ONLY.
 */
public class MarqueeScreenRenderer implements BlockEntityRenderer<MarqueeScreenBlockEntity, MarqueeRenderState> {

    static final Identifier IDLE = Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID,
            "textures/block/marquee_idle.png");

    /** The panel's bottom edge (model px 2) and its front face (z = 10 px), in blocks. */
    private static final float BOTTOM = 2f / 16f;
    private static final float FACE_Z = 10f / 16f - 0.01f;

    @Override
    public MarqueeRenderState createRenderState() {
        return new MarqueeRenderState();
    }

    @Override
    public int getViewDistance() {
        return ArcadeClientConfig.screenRenderRange();
    }

    private static int scaleOf(BlockState state) {
        return state.hasProperty(ArcadeBlockProperties.SCALE) ? state.getValue(ArcadeBlockProperties.SCALE) : 1;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(MarqueeScreenBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        int scale = scaleOf(be.getBlockState());
        double half = scale / 2.0;
        double height = BOTTOM + scale * 2.0 / 3.0;
        return new AABB(pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + Math.max(1.0, height), pos.getZ() + 0.5 + half);
    }

    @Override
    public boolean shouldRender(MarqueeScreenBlockEntity be, Vec3 cameraPos) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.FACING)) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        int scale = scaleOf(state);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + BOTTOM + scale / 3.0;
        double cz = pos.getZ() + 0.5;
        double range = getViewDistance() + scale;
        double dx = cameraPos.x - cx;
        double dy = cameraPos.y - cy;
        double dz = cameraPos.z - cz;
        if (dx * dx + dy * dy + dz * dz > range * range) {
            ArcadeBerStats.culled();
            return false;
        }
        Direction facing = state.getValue(ArcadeBlockProperties.FACING);
        boolean front = dx * facing.getStepX() + dz * facing.getStepZ() > -0.25;
        if (!front) {
            ArcadeBerStats.culled();
        }
        return front;
    }

    @Override
    public void extractRenderState(MarqueeScreenBlockEntity be, MarqueeRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        ArcadeBerStats.frame(be.getLevel() == null ? 0L : be.getLevel().getGameTime(), partialTick);
        long t0 = ArcadeBerStats.start();
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(ArcadeBlockProperties.FACING) ? state.getValue(ArcadeBlockProperties.FACING)
                : Direction.NORTH;
        s.quarterTurns = ArcadeCabinetRenderer.quarterTurns(facing);
        s.scale = scaleOf(state);
        long now = ClientMatches.now();
        s.main = CabinetScreens.prepareMarquee(be, now);
        s.strip = s.main == null ? null : CabinetScreens.prepareStrip(be, now);
        s.idle = s.main == null && be.clientDisplayOf() == null;
        if (s.main == null && !s.idle) {
            ArcadeBerStats.culled();
        }
        ArcadeBerStats.stop(t0);
    }

    @Override
    public void submit(MarqueeRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        int q = s.quarterTurns;
        float w = s.scale;
        float h = s.scale * 2f / 3f;
        float left = 0.5f + w / 2f; // the viewer's left edge (a north face is seen with +x on the left)
        float right = 0.5f - w / 2f;
        if (s.idle || s.main != null) {
            ArcadeBerStats.submitted();
            ArcadeBerStats.vertices(s.strip != null ? RenderPlan.MARQUEE_MAX : RenderPlan.QUAD_2SIDED);
        }
        if (s.idle) {
            collector.order(1).submitCustomGeometry(poseStack, ArcadeCabinetRenderer.renderType(IDLE),
                    (pose, c) -> quad(c, pose, q, left, right, BOTTOM, BOTTOM + h, 0f, 1f));
            return;
        }
        Identifier main = s.main;
        if (main == null) {
            return;
        }
        if (s.strip == null) {
            // idle board: one square, centred
            float sl = 0.5f + h / 2f;
            float sr = 0.5f - h / 2f;
            collector.order(1).submitCustomGeometry(poseStack, ArcadeCabinetRenderer.renderType(main),
                    (pose, c) -> quad(c, pose, q, sl, sr, BOTTOM, BOTTOM + h, 0f, 1f));
            return;
        }
        float split = left - h; // left two-thirds: the host's screen; right third: the strip
        Identifier strip = s.strip;
        collector.order(1).submitCustomGeometry(poseStack, ArcadeCabinetRenderer.renderType(main),
                (pose, c) -> quad(c, pose, q, left, split, BOTTOM, BOTTOM + h, 0f, 1f));
        collector.order(1).submitCustomGeometry(poseStack, ArcadeCabinetRenderer.renderType(strip),
                (pose, c) -> quad(c, pose, q, split, right, BOTTOM, BOTTOM + h, 0f, 0.5f));
    }

    /**
     * A double-sided quad on the face plane: x from {@code x0} (viewer's left) to {@code x1}, y from {@code y0} to
     * {@code y1}, u from {@code u0} to {@code u1}, v 0..1; turned to the block's facing.
     */
    static void quad(VertexConsumer c, PoseStack.Pose pose, int q, float x0, float x1, float y0, float y1, float u0,
            float u1) {
        float[][] corners = {
            turn(x0, y1, q), turn(x1, y1, q), turn(x1, y0, q), turn(x0, y0, q)
        };
        float[] uv = {u0, 0f, u1, 0f, u1, 1f, u0, 1f};
        float[] n = normal(q);
        for (int i = 0; i < 4; i++) {
            vertex(c, pose, corners[i], uv[i * 2], uv[i * 2 + 1], n[0], n[1], n[2]);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(c, pose, corners[i], uv[i * 2], uv[i * 2 + 1], -n[0], -n[1], -n[2]);
        }
    }

    /** A point on the north face plane (block units) turned {@code q} quarter turns about the block centre. */
    static float[] turn(float x, float y, int q) {
        float z = FACE_Z;
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {1f - z, y, x};
            case 2 -> new float[] {1f - x, y, 1f - z};
            case 3 -> new float[] {z, y, 1f - x};
            default -> new float[] {x, y, z};
        };
    }

    private static float[] normal(int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {1f, 0f, 0f};
            case 2 -> new float[] {0f, 0f, 1f};
            case 3 -> new float[] {-1f, 0f, 0f};
            default -> new float[] {0f, 0f, -1f};
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
