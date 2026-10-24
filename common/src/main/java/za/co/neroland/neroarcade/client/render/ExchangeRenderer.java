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

import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.block.Half;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.exchange.ArcadeExchangeBlockEntity;

/**
 * The Arcade Exchange kiosk (004 §4.5): the upper half's tilted listing screen scrolls the Exchange's top games
 * (title and heart count, one row every 2 s; "NO LISTINGS" when empty) from the rows the server syncs to the kiosk,
 * drawn through the shared {@link CabinetScreens} pool, and the "XCHG" wordmark sits on the magenta header bar.
 *
 * <p><b>Culling — never blanket true:</b> the text is culled past {@link RenderPlan#TEXT_RANGE} blocks (or the
 * client's shorter screen range) and behind the screen's plane; NeoForge also frustum-tests
 * {@link #getRenderBoundingBox}. At most {@link RenderPlan#EXCHANGE_MAX} vertices plus the four-glyph wordmark.
 * CLIENT-ONLY.
 */
public class ExchangeRenderer implements BlockEntityRenderer<ArcadeExchangeBlockEntity, ExchangeRenderer.State> {

    private static final RenderType FONT_TYPE = RenderTypes.entityTranslucentEmissive(ArcadeQuads.FONT);
    /** The screen element's front and tilt origin (upper half, model px). */
    private static final float SCREEN_Z = 2f;
    /** The header glow bar: x 2..14, y 12..14 of the upper half, front at z = 2. */
    private static final float HEADER_Z = 1.9f;
    private static final float HEADER_TOP = 16f + 13.75f;
    private static final float HEADER_H = 1.5f;
    public static final String WORDMARK = "XCHG";

    /** What the kiosk draws this frame. */
    public static class State extends BlockEntityRenderState {
        public int quarterTurns;
        public @Nullable Identifier screen;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return Math.min(RenderPlan.TEXT_RANGE, za.co.neroland.neroarcade.client.ArcadeClientConfig.screenRenderRange());
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(ArcadeExchangeBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }

    @Override
    public boolean shouldRender(ArcadeExchangeBlockEntity be, Vec3 cameraPos) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.HALF) || state.getValue(ArcadeBlockProperties.HALF) != Half.LOWER) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        double dx = cameraPos.x - pos.getX() - 0.5;
        double dz = cameraPos.z - pos.getZ() - 0.5;
        boolean near = RenderPlan.inRange(dx, cameraPos.y - pos.getY() - 1.5, dz, getViewDistance());
        Direction facing = state.getValue(ArcadeBlockProperties.FACING);
        boolean front = dx * facing.getStepX() + dz * facing.getStepZ() > -0.25;
        if (!near || !front) {
            ArcadeBerStats.culled();
        }
        return near && front;
    }

    @Override
    public void extractRenderState(ArcadeExchangeBlockEntity be, State s, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        ArcadeBerStats.frame(be.getLevel() == null ? 0L : be.getLevel().getGameTime(), partialTick);
        long t0 = ArcadeBerStats.start();
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(ArcadeBlockProperties.FACING) ? state.getValue(ArcadeBlockProperties.FACING)
                : Direction.NORTH;
        s.quarterTurns = ArcadeCabinetRenderer.quarterTurns(facing);
        s.screen = CabinetScreens.prepareKiosk(be, ClientMatches.now());
        ArcadeBerStats.stop(t0);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        long t0 = ArcadeBerStats.start();
        ArcadeBerStats.submitted();
        int q = s.quarterTurns;
        Identifier screen = s.screen;
        int vertices = 0;
        if (screen != null) {
            collector.order(1).submitCustomGeometry(poseStack, ArcadeCabinetRenderer.renderType(screen),
                    (pose, c) -> ArcadeCabinetRenderer.screenQuad(c, pose, q, SCREEN_Z));
            vertices += RenderPlan.QUAD_2SIDED;
        }
        collector.order(2).submitCustomGeometry(poseStack, FONT_TYPE, (pose, c) -> ArcadeQuads.text(c, pose, q,
                HEADER_Z, 8f, HEADER_TOP, HEADER_H, WORDMARK, ArcadeQuads.opaque(ArcadeColours.TEXT_LIGHT),
                ArcadeColours.FULL_BRIGHT));
        vertices += RenderPlan.glyphQuads(WORDMARK) * RenderPlan.QUAD;
        ArcadeBerStats.vertices(vertices);
        ArcadeBerStats.stop(t0);
    }
}
