package za.co.neroland.neroarcade.module.pack.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/** 008 §2.9 / 001 A10 {@code multiplayer/turns}: a stateless hot-seat rotation; only the seat on turn has a body. */
class TurnsMultiplayerTest {

    private static final PropertyValues TWO = PropertyValues.defaults(TurnsMultiplayer.INSTANCE);
    private static final PropertyValues THREE = MultiplayerPackTest.withInt(TurnsMultiplayer.INSTANCE, "seats", 3);
    /** turn_seconds 30 → 600 ticks per turn. */
    private static final int TURN = 30 * Budgets.TICKS_PER_SECOND;

    private static HeadlessMatch match(PropertyValues turns, int seats, int lives) {
        ResolvedGame game = MultiplayerPackTest.game(TurnsMultiplayer.INSTANCE, turns,
                MultiplayerPackTest.survive(120, lives));
        return MultiplayerPackTest.playing(game, seats, 13L);
    }

    /** Simulates until {@code state.tick() == tick} (the hooks of tick {@code tick - 1} have run). */
    private static void runTo(HeadlessMatch match, int tick) {
        while (match.state().tick() < tick) {
            match.run(1, HeadlessMatch.InputScript.randomWalk());
        }
    }

    private static boolean body(MatchState s, int seat) {
        int id = s.pawnId(seat);
        return id >= 0 && s.exists(id);
    }

    @Test
    void onlyActiveSeatHasBody() {
        HeadlessMatch match = match(TWO, 2, 1);
        MatchState s = match.state();
        assertTrue(body(s, 0) && body(s, 1), "every seat spawns at match start");
        runTo(match, 1);
        assertTrue(body(s, 0), "seat 0 is on turn at tick 0");
        assertTrue(!body(s, 1), "the waiting seat's body is lifted off the board");
        assertEquals(1, s.count(EntityKind.PAWN));
        assertTrue(s.seatAlive(1), "waiting is not dying");
        runTo(match, TURN - 1);
        assertEquals(1, s.count(EntityKind.PAWN));
        assertTrue(body(s, 0) && !body(s, 1));
    }

    @Test
    void rotatesEveryTurnSeconds() {
        assertEquals(0b01, TurnsMultiplayer.activeMask(0, 0b11, 30));
        assertEquals(0b01, TurnsMultiplayer.activeMask(TURN - 1, 0b11, 30));
        assertEquals(0b10, TurnsMultiplayer.activeMask(TURN, 0b11, 30), "tick 600 → seat 1 active");
        assertEquals(0b01, TurnsMultiplayer.activeMask(2 * TURN, 0b11, 30));
        assertEquals(0b10, TurnsMultiplayer.activeMask(200, 0b11, 10), "turn_seconds 10 → 200 ticks");

        HeadlessMatch match = match(TWO, 2, 1);
        MatchState s = match.state();
        runTo(match, TURN);
        assertTrue(body(s, 0) && !body(s, 1), "tick 599 still seat 0's");
        runTo(match, TURN + 1);
        assertTrue(!body(s, 0) && body(s, 1), "the hand-over at tick 600 swaps the bodies");
        assertEquals(1, s.count(EntityKind.PAWN));
        assertEquals(0b11, s.aliveSeatMask(), "the outgoing seat stays in the match");
        runTo(match, 2 * TURN + 1);
        assertTrue(body(s, 0) && !body(s, 1), "and back at tick 1200");
    }

    @Test
    void skipsDeadSeats() {
        assertEquals(0b001, TurnsMultiplayer.activeMask(0, 0b101, 30));
        assertEquals(0b100, TurnsMultiplayer.activeMask(TURN, 0b101, 30), "seat 1 is out: slot 1 is seat 2");
        assertEquals(0b001, TurnsMultiplayer.activeMask(2 * TURN, 0b101, 30));
        assertEquals(0b1000, TurnsMultiplayer.activeMask(TURN, 0b1010, 30));
        assertEquals(0, TurnsMultiplayer.activeMask(TURN, 0, 30), "nobody alive: nobody on turn");

        HeadlessMatch match = match(THREE, 3, 1);
        MatchState s = match.state();
        s.damageSeat(1, 1);
        assertEquals(0b101, s.aliveSeatMask());
        runTo(match, TURN + 1);
        assertTrue(body(s, 2), "seat 2 follows seat 0 when seat 1 is out");
        assertTrue(!body(s, 0) && !body(s, 1));
        assertTrue(match.result().isEmpty());
    }

    /** The seat on turn runs out mid-turn: the rotation re-derives and the next seat gets a body at once. */
    @Test
    void handsOverWhenTheActiveSeatIsOut() {
        HeadlessMatch match = match(TWO, 2, 1);
        MatchState s = match.state();
        runTo(match, 100);
        assertTrue(body(s, 0) && !body(s, 1));
        s.damageSeat(0, 1);
        assertEquals(0b10, s.aliveSeatMask());
        runTo(match, 101);
        assertTrue(body(s, 1), "seat 1 is now the only seat, so it is on turn");
        assertTrue(match.result().isEmpty(), "survive_time goes on for the remaining seat");
    }

    /** A seat hit on its own turn waits out its respawn timer like any other; turns never respawns it early. */
    @Test
    void activeSeatRespawnsOnTheRuntimeClock() {
        HeadlessMatch match = match(TWO, 2, 3);
        MatchState s = match.state();
        runTo(match, 50);
        s.damageSeat(0, 1);
        runTo(match, 51);
        assertTrue(!body(s, 0) && !body(s, 1), "between lives nobody is on the board");
        assertTrue(s.respawnTicks(0) > 0);
        runTo(match, 51 + Budgets.RESPAWN_TICKS);
        assertTrue(body(s, 0) && !body(s, 1));
        assertEquals(2, s.lives(0));
    }

    @Test
    void hudMaskIsPureFunctionOfState() {
        HeadlessMatch match = match(THREE, 3, 1);
        runTo(match, TURN + 17);
        MatchState s = match.state();
        long before = s.stateHash();
        ModuleContext ctx = MultiplayerPackTest.ctx(match);
        int mask = TurnsMultiplayer.INSTANCE.activeSeatMask(ctx);
        assertEquals(0b010, mask);
        assertEquals(mask, TurnsMultiplayer.INSTANCE.activeSeatMask(ctx), "same state, same answer");
        assertEquals(before, s.stateHash(), "asking never mutates the state");
        assertEquals(mask, TurnsMultiplayer.activeMask(s.tick(), s.aliveSeatMask(), 30));

        // a snapshot copy (what a client HUD holds) gives the same answer
        MatchState copy = s.copy();
        ModuleContext onCopy = new ModuleContext(copy, ctx.props(), Rng.view(copy), ctx.game(), copy.tick(), 0);
        assertEquals(mask, TurnsMultiplayer.INSTANCE.activeSeatMask(onCopy));
        assertEquals(before, copy.stateHash());
    }

    @Test
    void passesTheRuleOutcomeThrough() {
        HeadlessMatch match = match(TWO, 2, 1);
        for (RuleOutcome outcome : RuleOutcome.values()) {
            assertEquals(outcome, TurnsMultiplayer.INSTANCE.resolve(MultiplayerPackTest.ctx(match), outcome));
        }
    }

    @Test
    void oneSeatAlwaysOnTurnWithoutAnOpponent() {
        HeadlessMatch match = match(TWO, 1, 1);
        assertEquals(-1, match.runtime().opponentSeat(), "turns offers no runtime opponent");
        MatchState s = match.state();
        assertEquals(1, s.seatCount());
        runTo(match, TURN + 1);
        assertTrue(body(s, 0));
        assertEquals(0b1, TurnsMultiplayer.INSTANCE.activeSeatMask(MultiplayerPackTest.ctx(match)));
    }

    @Test
    void replayStable() {
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(TurnsMultiplayer.INSTANCE,
                MultiplayerPackTest.withInt(TurnsMultiplayer.INSTANCE, "turn_seconds", 10)), 2);
        PropertyValues fourFast = MultiplayerPackTest.with(
                MultiplayerPackTest.withInt(TurnsMultiplayer.INSTANCE, "seats", 4), "turn_seconds",
                new za.co.neroland.neroarcade.module.PropertyValue.IntValue(10));
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(TurnsMultiplayer.INSTANCE, fourFast), 4);
    }
}
