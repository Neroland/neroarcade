package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/**
 * The client's local-pawn prediction runs the real runtime over a copy of the authoritative state. With the
 * same inputs, three predicted ticks put the pawn exactly where the server puts it.
 */
class PredictionTest {

    @Test
    void shadowRuntimeMatchesTheAuthoritativePawn() {
        HeadlessMatch server = HeadlessMatch.of(TestModules.resolvedOf(), 1, 77L)
                .run(Budgets.COUNTDOWN_TICKS + 40, HeadlessMatch.InputScript.randomWalk());
        MatchState snapshot = server.state().copy();
        ArcadeRuntime shadow = new ArcadeRuntime(server.runtime().game(), snapshot, null);
        SeatInput[] inputs = {SeatInput.of(SeatInput.RIGHT, 0, 0), SeatInput.of(SeatInput.RIGHT, 0, 0),
                SeatInput.of(SeatInput.DOWN, 0, 0)};
        for (SeatInput input : inputs) {
            server.step(input);
            shadow.step(new SeatInput[] {input});
        }
        int serverPawn = server.state().pawnId(0);
        int shadowPawn = snapshot.pawnId(0);
        assertEquals(serverPawn, shadowPawn);
        assertEquals(server.state().get(serverPawn, Entities.X), snapshot.get(shadowPawn, Entities.X));
        assertEquals(server.state().get(serverPawn, Entities.Y), snapshot.get(shadowPawn, Entities.Y));
        assertEquals(server.state().stateHash(), snapshot.stateHash(), "no hazards: the whole state agrees");
    }
}
