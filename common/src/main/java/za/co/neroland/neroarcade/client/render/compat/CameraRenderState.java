package za.co.neroland.neroarcade.client.render.compat;

// Stand-in for 1.21.9's CameraRenderState below 1.21.9 (see LegacyBlockEntityRenderer). The renderers never read
// it. Compiled only below 1.21.9.
//? if <1.21.9 {
/*public final class CameraRenderState {

    public static final CameraRenderState INSTANCE = new CameraRenderState();

    private CameraRenderState() {
    }
}
*///?}
