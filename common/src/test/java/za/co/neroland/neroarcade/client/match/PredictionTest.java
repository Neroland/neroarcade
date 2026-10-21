package za.co.neroland.neroarcade.client.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The client half of local-pawn prediction (002 §8.3; Stage 1 proved the shadow equals the server with equal
 * inputs): the shadow never runs more than three ticks ahead, a prediction more than one cell off snaps to the
 * authoritative pawn (clearing the input ring), and an accurate one is rebuilt and replayed.
 */
class PredictionTest {

    private static final SeatInput RIGHT = SeatInput.of(SeatInput.RIGHT, 0, 0);

    private static HeadlessMatch playing(ResolvedGame game) {
        return HeadlessMatch.of(game, 1, 91L).run(Budgets.COUNTDOWN_TICKS + 10, HeadlessMatch.InputScript.idle());
    }

    @Test
    void neverMoreThanThreeTicksAhead() {
        ResolvedGame game = TestModules.resolvedOf();
        HeadlessMatch server = playing(game);
        LocalPrediction prediction = new LocalPrediction(game, 0);
        prediction.onSnapshot(server.state().copy());
        int advanced = 0;
        for (int i = 0; i < 8; i++) {
            if (prediction.tick(RIGHT)) {
                advanced++;
            }
        }
        assertEquals(LocalPrediction.HORIZON, advanced, "only three of eight ticks are predicted");
        assertEquals(LocalPrediction.HORIZON, prediction.ahead());
        assertEquals(server.state().tick() + LocalPrediction.HORIZON, prediction.shadow().tick());
    }

    @Test
    void accuratePredictionIsReplayedNotSnapped() {
        ResolvedGame game = TestModules.resolvedOf();
        HeadlessMatch server = playing(game);
        LocalPrediction prediction = new LocalPrediction(game, 0);
        prediction.onSnapshot(server.state().copy());
        prediction.tick(SeatInput.NONE);
        server.step(SeatInput.NONE);
        prediction.onSnapshot(server.state().copy());
        assertEquals(0, prediction.snaps());
        assertEquals(1, prediction.ahead(), "the remembered input is re-applied on the new snapshot");
    }

    @Test
    void moreThanOneCellOffSnaps() {
        ResolvedGame game = TestModules.resolvedOf();
        HeadlessMatch server = playing(game);
        LocalPrediction prediction = new LocalPrediction(game, 0);
        prediction.onSnapshot(server.state().copy());
        prediction.tick(SeatInput.NONE); // predicted: the pawn stays put at tick t+1
        server.step(SeatInput.NONE);
        MatchState truth = server.state().copy();
        int pawn = truth.pawnId(0);
        assertTrue(pawn >= 0);
        truth.set(pawn, Entities.X, truth.get(pawn, Entities.X) + 2 * MatchState.FP); // the server says: 2 cells away
        prediction.onSnapshot(truth);
        assertEquals(1, prediction.snaps(), "a two-cell misprediction snaps");
        assertEquals(0, prediction.ahead(), "after a snap the pawn is exactly where the server has it");
        assertNotNull(prediction.shadow());
        assertEquals(truth.get(pawn, Entities.X), prediction.x(pawn));
        assertTrue(prediction.owns(truth, pawn), "the seat's own pawn is drawn from the shadow");
    }

    @Test
    void oneCellIsWithinTolerance() {
        assertFalse(LocalPrediction.shouldSnap(0, 0, MatchState.FP, MatchState.FP));
        assertTrue(LocalPrediction.shouldSnap(0, 0, MatchState.FP + 1, 0));
        assertTrue(LocalPrediction.shouldSnap(0, 0, 0, -MatchState.FP - 1));
    }

    @Test
    void interpolationLerpsInFixedPointAndSnapsLongJumps() {
        assertEquals(128, Interpolation.lerp(0, 256, 128));
        assertEquals(0, Interpolation.lerp(0, 256, -5));
        assertEquals(256, Interpolation.lerp(0, 256, 999));
        assertEquals(0, Interpolation.lerp(0, 3 * 256, 100), "a three-cell jump (wrap, respawn) does not slide");
        assertEquals(3 * 256, Interpolation.lerp(0, 3 * 256, 200));
        assertEquals(0, Interpolation.partial(10L << 8, 8, 10), "at the latest arrival: the previous position");
        assertEquals(128, Interpolation.partial((11L << 8), 8, 10), "half an interval later: half way");
        assertEquals(256, Interpolation.partial((20L << 8), 8, 10), "never extrapolates past the latest");
    }
}
