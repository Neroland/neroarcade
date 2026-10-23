package za.co.neroland.neroarcade.module.pack.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.MatchState;

/** 008 §2.9 {@code multiplayer/coop}: a shared board, one life pool by default, the rule's outcome as is. */
class CoopMultiplayerTest {

    private static final PropertyValues SHARED = PropertyValues.defaults(CoopMultiplayer.INSTANCE);
    private static final PropertyValues SEPARATE = MultiplayerPackTest.withBool(CoopMultiplayer.INSTANCE,
            "shared_lives", false);

    /** Two seats, survive_time 60 s with 3 lives each (the pool is seat 0's starting lives). */
    private static HeadlessMatch twoSeats(PropertyValues coop) {
        ResolvedGame game = MultiplayerPackTest.game(CoopMultiplayer.INSTANCE, coop, MultiplayerPackTest.survive(60, 3));
        return MultiplayerPackTest.playing(game, 2, 7L);
    }

    private static void idle(HeadlessMatch match, int ticks) {
        match.run(ticks, HeadlessMatch.InputScript.idle());
    }

    @Test
    void sharedLivesPool() {
        HeadlessMatch match = twoSeats(SHARED);
        MatchState s = match.state();
        assertEquals(3, s.sharedLives(), "the pool is the rule's per-seat lives");
        assertEquals(3, s.lives(0));
        assertEquals(3, s.lives(1));

        s.damageSeat(0, 1);
        assertEquals(2, s.sharedLives(), "one death costs the pool");
        assertEquals(2, s.lives(0));
        assertEquals(2, s.lives(1), "seat 1 paid for seat 0's death");

        s.damageSeat(1, 1);
        assertEquals(1, s.sharedLives());
        idle(match, Budgets.RESPAWN_TICKS + 1 + Budgets.RESPAWN_GRACE_TICKS);
        assertTrue(s.pawnId(0) >= 0 && s.pawnId(1) >= 0, "both seats respawned while the pool lasts");
        assertTrue(match.result().isEmpty());

        s.damageSeat(0, 1);
        assertEquals(0, s.sharedLives());
        assertEquals(0, s.aliveSeatMask(), "an empty pool puts every seat out");
        idle(match, 1);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.LOSE, result.outcome(), "coop passes the rule's LOSE through");
        assertEquals(0, result.winnersMask());
    }

    @Test
    void separateLives() {
        HeadlessMatch match = twoSeats(SEPARATE);
        MatchState s = match.state();
        assertEquals(-1, s.sharedLives());
        s.damageSeat(0, 1);
        assertEquals(2, s.lives(0));
        assertEquals(3, s.lives(1), "seat 1 keeps its own lives");
        s.damageSeat(0, 5);
        idle(match, Budgets.RESPAWN_TICKS + 1 + Budgets.RESPAWN_GRACE_TICKS);
        s.damageSeat(0, 5);
        assertEquals(0b10, s.aliveSeatMask(), "seat 0 is out alone");
        idle(match, 1);
        assertTrue(match.result().isEmpty(), "survive_time goes on while seat 1 lives");
    }

    @Test
    void sharedBoardAndRulePassThrough() {
        HeadlessMatch match = twoSeats(SHARED);
        idle(match, 1);
        MatchState s = match.state();
        assertEquals(SeatLayout.shared(2, s.width(), s.height()), s.layout());
        assertEquals(0b11, CoopMultiplayer.INSTANCE.activeSeatMask(MultiplayerPackTest.ctx(match)));
        for (RuleOutcome outcome : RuleOutcome.values()) {
            assertEquals(outcome, CoopMultiplayer.INSTANCE.resolve(MultiplayerPackTest.ctx(match), outcome));
        }
    }

    @Test
    void timedWinNamesEverySurvivor() {
        ResolvedGame game = MultiplayerPackTest.game(CoopMultiplayer.INSTANCE, SHARED, MultiplayerPackTest.survive(10, 3));
        HeadlessMatch match = MultiplayerPackTest.playing(game, 2, 3L);
        match.runToEnd(HeadlessMatch.InputScript.idle(), 400);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(0b11, result.winnersMask());
    }

    @Test
    void replayStable() {
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(CoopMultiplayer.INSTANCE, SHARED), 2);
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(CoopMultiplayer.INSTANCE,
                MultiplayerPackTest.withInt(CoopMultiplayer.INSTANCE, "seats", 4)), 4);
    }
}
