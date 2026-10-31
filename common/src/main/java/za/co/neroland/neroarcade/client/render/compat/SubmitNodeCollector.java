package za.co.neroland.neroarcade.client.render.compat;

// Stand-in for 1.21.9's SubmitNodeCollector below 1.21.9 (see LegacyBlockEntityRenderer): custom geometry is drawn
// immediately into the frame's MultiBufferSource. The order index only sorts submissions on 1.21.9+, so it is
// ignored here. Compiled only below 1.21.9.
//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class SubmitNodeCollector {

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public SubmitNodeCollector order(int order) {
        return this;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType type, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), buffers.getBuffer(type));
    }

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
*///?}
