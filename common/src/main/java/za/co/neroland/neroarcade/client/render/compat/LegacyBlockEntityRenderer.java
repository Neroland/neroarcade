package za.co.neroland.neroarcade.client.render.compat;

// Before 1.21.9 a block-entity renderer draws straight into a MultiBufferSource from one render(..) call; 1.21.9
// split that into extractRenderState(..) + submit(..) with a SubmitNodeCollector. The renderers are written for the
// split API; on older lines the build points them at this interface (see legacyRenames), which runs the same two
// steps from render(..). Compiled only below 1.21.9.
//? if <1.21.5 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public interface LegacyBlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        extends BlockEntityRenderer<T> {

    S createRenderState();

    default void extractRenderState(T be, S state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);

    @Override
    default void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            int overlay) {
        S state = createRenderState();
        state.lightCoords = light;
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        extractRenderState(be, state, partialTick, cameraPos, null);
        submit(state, poseStack, new SubmitNodeCollector(buffers), CameraRenderState.INSTANCE);
    }
}
*///?} else if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public interface LegacyBlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        extends BlockEntityRenderer<T> {

    S createRenderState();

    default void extractRenderState(T be, S state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);

    @Override
    default void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            int overlay, Vec3 cameraPos) {
        S state = createRenderState();
        state.lightCoords = light;
        extractRenderState(be, state, partialTick, cameraPos, null);
        submit(state, poseStack, new SubmitNodeCollector(buffers), CameraRenderState.INSTANCE);
    }
}
*///?}
