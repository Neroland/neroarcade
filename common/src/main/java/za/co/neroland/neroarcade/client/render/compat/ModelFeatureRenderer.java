package za.co.neroland.neroarcade.client.render.compat;

// Stand-in for 1.21.9's ModelFeatureRenderer.CrumblingOverlay below 1.21.9 (see LegacyBlockEntityRenderer). Always
// null there; the renderers never read it. Compiled only below 1.21.9.
//? if <1.21.9 {
/*public final class ModelFeatureRenderer {

    private ModelFeatureRenderer() {
    }

    public static final class CrumblingOverlay {

        private CrumblingOverlay() {
        }
    }
}
*///?}
