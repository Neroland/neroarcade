package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/** 001 A3, step 8b: goal cells score against their seat. */
class GoalCellTest {

    private static HeadlessMatch duel() {
        return RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(new RuntimeStubs.DriftHazard("hazard/drift"),
                TestModules.multiplayer(false), RuntimeStubs.lives(3)), 2, 4L));
    }

    @Test
    void goalValuesAreAppendOnly() {
        assertEquals(6, Cell.GOAL_0);
        assertEquals(9, Cell.GOAL_3);
        assertEquals(1, Cell.goalSeat(Cell.GOAL_1));
        assertEquals(-1, Cell.goalSeat(Cell.LANE));
        assertFalse(Cell.blocks(Cell.GOAL_2));
        assertEquals(Cell.GOAL_3, Cell.goal(3));
    }

    @Test
    void aHazardOnAGoalCostsThatSeatALifeAndIsRemoved() {
        HeadlessMatch match = duel();
        MatchState s = match.state();
        s.setCell(8, 8, Cell.GOAL_1);
        int hazard = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(8), RuntimeStubs.at(7), -1, 0);
        s.set(hazard, Entities.VY, MatchState.FP);
        match.step();
        assertEquals(2, s.lives(1));
        assertEquals(3, s.lives(0));
        assertFalse(s.exists(hazard));
    }

    @Test
    void aPawnStandingOnAGoalIsUntouched() {
        HeadlessMatch match = duel();
        MatchState s = match.state();
        int pawn = s.pawnId(0);
        s.setCell(s.get(pawn, Entities.X) >> MatchState.FP_SHIFT, s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT,
                Cell.GOAL_0);
        match.run(5, HeadlessMatch.InputScript.idle());
        assertEquals(3, s.lives(0));
        assertTrue(s.exists(pawn));
    }

    @Test
    void aSeatsOwnProjectileCrossesItsOwnGoal() {
        HeadlessMatch match = duel();
        MatchState s = match.state();
        s.setCell(8, 8, Cell.GOAL_1);
        int own = s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(8), RuntimeStubs.at(8), 1, 0);
        match.step();
        assertEquals(3, s.lives(1));
        assertTrue(s.exists(own));
        s.remove(own);
        int enemy = s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(8), RuntimeStubs.at(8), 0, 0);
        match.step();
        assertEquals(2, s.lives(1));
        assertFalse(s.exists(enemy));
    }
}
