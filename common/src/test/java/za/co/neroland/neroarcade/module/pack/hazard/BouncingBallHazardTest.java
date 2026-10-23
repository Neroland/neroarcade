package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.hazard.HazardPackTest.Kit;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class BouncingBallHazardTest {

    private static ResolvedGame solo() {
        return TestModules.resolvedOf(BouncingBallHazard.INSTANCE);
    }

    /** Launches ball {@code id} at a cell with a velocity, past its serve delay. */
    private static void launch(MatchState s, int id, int cx, int cy, int vx, int vy) {
        s.set(id, Entities.X, Kit.at(cx));
        s.set(id, Entities.Y, Kit.at(cy));
        s.set(id, Entities.VX, vx);
        s.set(id, Entities.VY, vy);
        s.setAux(id, HazardAux.withCounter(s.aux(id), 0));
    }

    @Test
    void servesAfterDelay() {
        ResolvedGame game = solo();
        HeadlessMatch match = Kit.playing(game, 1, 8L);
        MatchState s = match.state();
        List<Integer> balls = Kit.ids(s, EntityKind.HAZARD, 0);
        assertEquals(1, balls.size());
        int ball = balls.get(0);
        assertEquals(8, Kit.cellX(s, ball), "served at w/2 + (0 - 1/2)");
        assertEquals(8, Kit.cellY(s, ball));
        for (int t = 0; t < BouncingBallHazard.SERVE_DELAY - 1; t++) {
            BouncingBallHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
            assertEquals(0, s.get(ball, Entities.VX));
            assertEquals(0, s.get(ball, Entities.VY));
            assertEquals(Kit.at(8), s.get(ball, Entities.X), "still while waiting");
        }
        BouncingBallHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertEquals(192, Math.abs(s.get(ball, Entities.VX)));
        assertEquals(192, Math.abs(s.get(ball, Entities.VY)));
    }

    @Test
    void reflectsAtWall() {
        ResolvedGame game = solo();
        HeadlessMatch match = Kit.playing(game, 1, 8L);
        MatchState s = match.state();
        int ball = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        launch(s, ball, 1, 8, -192, 0);
        BouncingBallHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertEquals(192, s.get(ball, Entities.VX), "the left wall flips VX");
        assertEquals(1, Kit.cellX(s, ball), "and stops the axis inside cell 1");
        BouncingBallHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        BouncingBallHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertEquals(2, Kit.cellX(s, ball), "and then travels right");
    }

    @Test
    void goalAwardsOthers() {
        ResolvedGame game = TestModules.resolvedOf(BouncingBallHazard.INSTANCE, TestModules.multiplayer(false));
        HeadlessMatch match = Kit.playing(game, 2, 8L);
        MatchState s = match.state();
        s.setLives(0, 3);
        s.setLives(1, 3);
        int ball = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        s.setCell(5, 4, Cell.GOAL_1);
        launch(s, ball, 5, 5, 0, -192);
        match.step();
        assertFalse(s.exists(ball), "the goal pass removed it");
        assertEquals(1, s.score(0));
        assertEquals(0, s.score(1));
        assertEquals(2, s.lives(1));
        assertEquals(3, s.lives(0));
        match.step();
        assertEquals(1, Kit.count(s, EntityKind.HAZARD, 0), "re-served on the next tick");
        int served = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        assertEquals(BouncingBallHazard.SERVE_DELAY, HazardAux.counter(s.aux(served)));
    }

    @Test
    void englishClamped() {
        ResolvedGame game = solo();
        HeadlessMatch match = Kit.playing(game, 1, 8L);
        MatchState s = match.state();
        int ball = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        int pawn = s.pawnId(0);
        Kit.putPawn(s, 0, 8, 10);
        s.set(pawn, Entities.VX, 192);
        s.set(pawn, Entities.VY, 0);
        // entering the bat's cell from above only (x - vx stays in cell 8), already at the along-axis maximum
        s.set(ball, Entities.X, Kit.at(8) + 64);
        s.set(ball, Entities.Y, Kit.at(10));
        s.set(ball, Entities.VX, 192);
        s.set(ball, Entities.VY, 192);
        BouncingBallHazard.INSTANCE.onDeflected(Kit.ctx(s, game, 0), 0, ball, pawn);
        assertEquals(192, s.get(ball, Entities.VX), "clamped to speed x 64");
        assertEquals(-192, s.get(ball, Entities.VY), "reflected on the entry axis");
        // a near-flat ball gets its perpendicular component raised to 64
        s.set(ball, Entities.X, Kit.at(8));
        s.set(ball, Entities.Y, Kit.at(10));
        s.set(ball, Entities.VX, -192);
        s.set(ball, Entities.VY, 0);
        BouncingBallHazard.INSTANCE.onDeflected(Kit.ctx(s, game, 0), 0, ball, pawn);
        assertTrue(Math.abs(s.get(ball, Entities.VY)) >= BouncingBallHazard.MIN_PERPENDICULAR);
        assertTrue(Math.abs(s.get(ball, Entities.VX)) <= 192);
        assertNotEquals(0, s.get(ball, Entities.VY));
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(solo(), 1);
    }
}
