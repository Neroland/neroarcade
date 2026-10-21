package za.co.neroland.neroarcade.client;

import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * The client-local look settings ({@code client.*} in {@code config/neroarcade.properties}, never synced or sent):
 * scanlines and a shorter screen draw distance. The draw distance can only shrink the server's
 * {@code screenRenderRange}, never extend it. CLIENT-ONLY.
 */
public final class ArcadeClientConfig {

    private ArcadeClientConfig() {
    }

    public static boolean scanlines() {
        try {
            return NeroArcadeConfig.CLIENT_SCANLINES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Blocks within which arcade screens draw: the server's range, or this computer's shorter one. */
    public static int screenRenderRange() {
        int server = Budgets.screenRenderRange();
        int local;
        try {
            local = NeroArcadeConfig.CLIENT_SCREEN_RENDER_RANGE.get();
        } catch (RuntimeException e) {
            local = 0;
        }
        return effectiveRange(server, local);
    }

    /** {@code local} of 0 means "the server's"; otherwise the smaller of the two. Pure. */
    public static int effectiveRange(int server, int local) {
        return local <= 0 ? server : Math.min(server, local);
    }
}
