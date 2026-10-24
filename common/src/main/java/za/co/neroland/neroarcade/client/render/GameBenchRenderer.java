package za.co.neroland.neroarcade.client.render;

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

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.bench.GameBenchBlockEntity;
import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * The Game Bench holo-rack (004 §4.2), drawn only while {@code editing=true}: ten 2×2-px chip billboards in a 5×2
 * grid hovering over the holo plate, each in its slot colour when that slot holds a chip ({@code #3a4458} when
 * empty), bobbing ±1 px on a 2 s sine, and above them a 16×16-px preview quad showing the draft's headless demo
 * (repainted at 8 Hz from the shared {@link CabinetScreens} pool). Not editing: nothing.
 *
 * <p><b>Culling — never blanket true:</b> {@code editing} must be set, the camera within
 * {@link RenderPlan#PROP_RANGE} blocks (or the client's shorter screen range) of the rack, and NeoForge also
 * frustum-tests {@link #getRenderBoundingBox}. At most {@link RenderPlan#BENCH_MAX} vertices. CLIENT-ONLY.
 */
public class GameBenchRenderer implements BlockEntityRenderer<GameBenchBlockEntity, GameBenchRenderer.State> {

    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(ArcadeQuads.WHITE);
    /** The rack plane (model px): a vertical sheet through the bench's middle, facing the front. */
    private static final float RACK_Z = 8f;
    private static final float[] COLUMNS = {3.5f, 5.75f, 8f, 10.25f, 12.5f};
    private static final float ROW_TOP = 23f;
    private static final float ROW_GAP = 3f;
    private static final float CHIP = 2f;
    private static final float PREVIEW_BOTTOM = 26f;
    private static final float PREVIEW = 16f;

    /** What the bench draws this frame. */
    public static class State extends BlockEntityRenderState {
        public int quarterTurns;
        public boolean editing;
        public int rackMask;
        public float bob;
        public @Nullable Identifier preview;
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
    public AABB getRenderBoundingBox(GameBenchBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
    }

    @Override
    public boolean shouldRender(GameBenchBlockEntity be, Vec3 cameraPos) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.EDITING) || !state.getValue(ArcadeBlockProperties.EDITING)) {
            return false; // nothing to draw: not counted as a cull
        }
        BlockPos pos = be.getBlockPos();
        boolean near = RenderPlan.inRange(cameraPos.x - pos.getX() - 0.5, cameraPos.y - pos.getY() - 1.75,
                cameraPos.z - pos.getZ() - 0.5, getViewDistance());
        if (!near) {
            ArcadeBerStats.culled();
        }
        return near;
    }

    @Override
    public void extractRenderState(GameBenchBlockEntity be, State s, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        ArcadeBerStats.frame(be.getLevel() == null ? 0L : be.getLevel().getGameTime(), partialTick);
        long t0 = ArcadeBerStats.start();
        BlockState state = be.getBlockState();
        s.editing = state.hasProperty(ArcadeBlockProperties.EDITING) && state.getValue(ArcadeBlockProperties.EDITING);
        Direction facing = state.hasProperty(ArcadeBlockProperties.FACING) ? state.getValue(ArcadeBlockProperties.FACING)
                : Direction.NORTH;
        s.quarterTurns = ArcadeCabinetRenderer.quarterTurns(facing);
        s.rackMask = be.clientRackMask();
        long now = ClientMatches.now();
        s.bob = RenderPlan.bob(now + partialTick);
        s.preview = s.editing ? CabinetScreens.prepareBench(be, now) : null;
        ArcadeBerStats.stop(t0);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!s.editing) {
            return;
        }
        long t0 = ArcadeBerStats.start();
        ArcadeBerStats.submitted();
        int q = s.quarterTurns;
        int mask = s.rackMask;
        float bob = s.bob;
        collector.order(1).submitCustomGeometry(poseStack, GLOW, (pose, c) -> {
            ModuleType[] types = ModuleType.values();
            for (int i = 0; i < types.length && i < RenderPlan.RACK_CHIPS; i++) {
                int colour = (mask & (1 << i)) != 0 ? types[i].colour : ArcadeColours.TRIM;
                float cx = COLUMNS[i % 5];
                float top = ROW_TOP - (i / 5) * ROW_GAP + bob;
                ArcadeQuads.wall(c, pose, q, RACK_Z, cx + CHIP / 2f, cx - CHIP / 2f, top - CHIP, top,
                        ArcadeQuads.WHITE_UV, ArcadeQuads.opaque(colour), ArcadeColours.FULL_BRIGHT, true);
            }
        });
        int vertices = RenderPlan.RACK_CHIPS * RenderPlan.QUAD_2SIDED;
        Identifier preview = s.preview;
        if (preview != null) {
            float y0 = PREVIEW_BOTTOM + bob;
            collector.order(1).submitCustomGeometry(poseStack, ArcadeCabinetRenderer.renderType(preview),
                    (pose, c) -> ArcadeQuads.wall(c, pose, q, RACK_Z, 8f + PREVIEW / 2f, 8f - PREVIEW / 2f, y0,
                            y0 + PREVIEW, new float[] {0f, 0f, 1f, 1f}, 0xFFFFFFFF, ArcadeColours.FULL_BRIGHT, true));
            vertices += RenderPlan.QUAD_2SIDED;
        }
        ArcadeBerStats.vertices(vertices);
        ArcadeBerStats.stop(t0);
    }
}
