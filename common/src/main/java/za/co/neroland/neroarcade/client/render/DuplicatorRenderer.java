package za.co.neroland.neroarcade.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.duplicator.CartridgeDuplicatorBlockEntity;

/**
 * The Cartridge Duplicator (004 §4.4): while {@code copying=true} (the static model then leaves its platter out) the
 * platter spins one turn every 40 ticks, and a 6×1-px cyan bar on the front face fills over the copy; when a copy
 * that ran to the end stops, the bar flashes green for {@value CopyClock#FLASH_TICKS} ticks. The bar is timed on
 * this client ({@link CopyClock}) because the block state carries only {@code copying}.
 *
 * <p><b>Culling — never blanket true:</b> only while copying or flashing, within {@link RenderPlan#PROP_RANGE}
 * blocks, and NeoForge also frustum-tests {@link #getRenderBoundingBox}. At most {@link RenderPlan#DUPLICATOR_MAX}
 * vertices. CLIENT-ONLY.
 */
public class DuplicatorRenderer
        implements BlockEntityRenderer<CartridgeDuplicatorBlockEntity, DuplicatorRenderer.State> {

    private static final Identifier PLATTER = Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID,
            "textures/block/duplicator_platter.png");
    private static final RenderType PLATTER_TYPE = RenderTypes.entityCutout(PLATTER);
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(ArcadeQuads.WHITE);
    /** Platter: x 4..12, z 6..14 at y 8..9, spinning about [8, 8.5, 10] (Registry ids appendix). */
    private static final float PLATTER_Y = 9.01f;
    private static final float PLATTER_CX = 8f;
    private static final float PLATTER_CZ = 10f;
    private static final float PLATTER_HALF = 4f;
    /** The progress bar on the front face (z = 0): 6 px wide at x 5..11, one px tall at y 2..3. */
    private static final float BAR_Z = -0.02f;
    private static final float BAR_X0 = 11f;
    private static final float BAR_W = 6f;
    private static final float BAR_Y = 2f;

    private static final Map<Long, CopyClock> CLOCKS = new HashMap<>();

    /** What the duplicator draws this frame. */
    public static class State extends BlockEntityRenderState {
        public int quarterTurns;
        public boolean copying;
        public float degrees;
        public float progress;
        public boolean flash;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return Math.min(RenderPlan.PROP_RANGE, za.co.neroland.neroarcade.client.ArcadeClientConfig.screenRenderRange());
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(CartridgeDuplicatorBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos);
    }

    @Override
    public boolean shouldRender(CartridgeDuplicatorBlockEntity be, Vec3 cameraPos) {
        BlockPos pos = be.getBlockPos();
        long key = pos.asLong();
        boolean copying = copying(be.getBlockState());
        CopyClock clock = CLOCKS.get(key);
        long now = ClientMatches.now();
        if (!copying && (clock == null || clock.idle(now))) {
            if (clock != null) {
                CLOCKS.remove(key);
            }
            return false; // nothing to draw: not counted as a cull
        }
        boolean near = RenderPlan.inRange(cameraPos.x - pos.getX() - 0.5, cameraPos.y - pos.getY() - 0.5,
                cameraPos.z - pos.getZ() - 0.5, getViewDistance());
        if (!near) {
            ArcadeBerStats.culled();
        }
        return near;
    }

    private static boolean copying(BlockState state) {
        return state.hasProperty(ArcadeBlockProperties.COPYING) && state.getValue(ArcadeBlockProperties.COPYING);
    }

    @Override
    public void extractRenderState(CartridgeDuplicatorBlockEntity be, State s, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        ArcadeBerStats.frame(be.getLevel() == null ? 0L : be.getLevel().getGameTime(), partialTick);
        long t0 = ArcadeBerStats.start();
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(ArcadeBlockProperties.FACING) ? state.getValue(ArcadeBlockProperties.FACING)
                : Direction.NORTH;
        s.quarterTurns = ArcadeCabinetRenderer.quarterTurns(facing);
        long now = ClientMatches.now();
        s.copying = copying(state);
        CopyClock clock = CLOCKS.computeIfAbsent(be.getBlockPos().asLong(), k -> new CopyClock());
        s.progress = clock.observe(s.copying, now);
        s.flash = clock.flashing(now);
        s.degrees = RenderPlan.platterDegrees(now + partialTick);
        ArcadeBerStats.stop(t0);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!s.copying && !s.flash) {
            return;
        }
        long t0 = ArcadeBerStats.start();
        ArcadeBerStats.submitted();
        int q = s.quarterTurns;
        int vertices = 0;
        if (s.copying) {
            float degrees = s.degrees;
            int light = s.lightCoords;
            collector.order(0).submitCustomGeometry(poseStack, PLATTER_TYPE,
                    (pose, c) -> ArcadeQuads.spinningFloor(c, pose, q, PLATTER_CX, PLATTER_Y, PLATTER_CZ, PLATTER_HALF,
                            degrees, new float[] {0f, 0f, 1f, 1f}, 0xFFFFFFFF, light));
            vertices += RenderPlan.QUAD_2SIDED;
        }
        float fill = s.flash ? 1f : s.progress;
        if (fill > 0f) {
            int colour = ArcadeQuads.opaque(s.flash ? ArcadeColours.OK : ArcadeColours.CYAN);
            collector.order(1).submitCustomGeometry(poseStack, GLOW, (pose, c) -> ArcadeQuads.wall(c, pose, q, BAR_Z,
                    BAR_X0, BAR_X0 - BAR_W * fill, BAR_Y, BAR_Y + 1f, ArcadeQuads.WHITE_UV, colour,
                    ArcadeColours.FULL_BRIGHT, false));
            vertices += RenderPlan.QUAD;
        }
        ArcadeBerStats.vertices(vertices);
        ArcadeBerStats.stop(t0);
    }

    /** Disconnect: forget every clock. */
    public static void clear() {
        CLOCKS.clear();
    }
}
