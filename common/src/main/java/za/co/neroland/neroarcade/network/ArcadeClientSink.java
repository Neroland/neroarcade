package za.co.neroland.neroarcade.network;

import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;

/**
 * Where the client-bound payloads go once they reach the client thread. Common code declares the payloads with
 * handlers that forward here; the client module (screens, BER, HUD) {@link #install installs} its sink during
 * client setup. Until then every payload is ignored, so a dedicated server never touches client classes.
 *
 * <p>Handlers run on the client thread (each loader hops there before calling). A sink must never trust a
 * payload as more than a display hint.
 */
public interface ArcadeClientSink {

    ArcadeClientSink NONE = new ArcadeClientSink() {
    };

    default void onSnapshot(SnapshotPayload payload) {
    }

    default void onMatchPhase(MatchPhasePayload payload) {
    }

    default void onHudLegend(HudLegendPayload payload) {
    }

    default void onBenchState(BenchStatePayload payload) {
    }

    default void onBoardPage(BoardPagePayload payload) {
    }

    default void onExchangePage(ExchangePagePayload payload) {
    }

    /** Session end: drop every client-side mirror. */
    default void clear() {
    }

    /** Installs the client sink (client setup only). */
    static void install(ArcadeClientSink sink) {
        Holder.sink = sink == null ? NONE : sink;
    }

    static ArcadeClientSink get() {
        return Holder.sink;
    }

    /** The installed sink; {@link #NONE} until the client module installs one. */
    final class Holder {
        private static volatile ArcadeClientSink sink = NONE;

        private Holder() {
        }
    }
}
