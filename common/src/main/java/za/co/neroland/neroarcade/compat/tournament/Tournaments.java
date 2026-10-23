package za.co.neroland.neroarcade.compat.tournament;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.compat.Seams;

/**
 * Fan-out for finished, credited match results (003 §10): the installed {@link TournamentSeam} first, then every
 * listener registered here. A failing listener is logged by exception class and skipped; the others still run.
 * Results carry seat UUIDs (they are the leaderboard's input), so a listener must treat them as personal data.
 */
public final class Tournaments {

    private static final List<Consumer<BusMatchResult>> LISTENERS = new CopyOnWriteArrayList<>();

    private Tournaments() {
    }

    public static void onResult(Consumer<BusMatchResult> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** Test teardown. */
    public static boolean remove(Consumer<BusMatchResult> listener) {
        return LISTENERS.remove(listener);
    }

    public static void publish(BusMatchResult result) {
        try {
            Seams.tournament().onResult(result);
        } catch (RuntimeException | LinkageError e) {
            Seams.failed("tournament", e);
        }
        for (Consumer<BusMatchResult> listener : LISTENERS) {
            try {
                listener.accept(result);
            } catch (RuntimeException e) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] match result listener failed ({})", e.getClass().getName());
            }
        }
    }
}
