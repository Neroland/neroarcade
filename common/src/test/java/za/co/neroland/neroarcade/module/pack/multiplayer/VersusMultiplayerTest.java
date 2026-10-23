package za.co.neroland.neroarcade.module.pack.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Replay;

/** 008 §2.9 {@code multiplayer/versus}: one shared board; resolve turns any rule into a contest. */
class VersusMultiplayerTest {

    private static final PropertyValues TWO = PropertyValues.defaults(VersusMultiplayer.INSTANCE);
    private static final PropertyValues FOUR = MultiplayerPackTest.withInt(VersusMultiplayer.INSTANCE, "seats", 4);

    @AfterEach
    void restore() {
        TestModules.install();
    }

    private static HeadlessMatch match(PropertyValues versus, int seats, PropertyValues rule) {
        ResolvedGame game = MultiplayerPackTest.game(VersusMultiplayer.INSTANCE, versus, rule);
        return MultiplayerPackTest.playing(game, seats, 11L);
    }

    private static MatchResult step(HeadlessMatch match) {
        match.run(1, HeadlessMatch.InputScript.idle());
        return match.result().orElse(null);
    }

    @Test
    void lastAliveWinsEvenUnderSurviveTime() {
        HeadlessMatch match = match(TWO, 2, MultiplayerPackTest.survive(60, 1));
        assertEquals(null, step(match));
        match.state().damageSeat(1, 1);
        MatchResult result = step(match);
        assertEquals(MatchResult.Outcome.WIN, result.outcome(), "the survivor does not wait for the clock");
        assertEquals(0b01, result.winnersMask());
        assertTrue(result.ticks() < 60 * 20);
    }

    @Test
    void narrowsSharedWinByScore() {
        // end to end: both survive a 10 s clock, seat 1 has more points
        HeadlessMatch match = match(TWO, 2, MultiplayerPackTest.survive(10, 1));
        match.state().addScore(0, 3);
        match.state().addScore(1, 5);
        match.runToEnd(HeadlessMatch.InputScript.idle(), 400);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(0b10, result.winnersMask(), "survive_time named both; versus keeps the best score");

        // unit: ties keep every tied seat; a single winner is left alone
        HeadlessMatch live = match(FOUR, 4, MultiplayerPackTest.survive(60, 1));
        MatchState s = live.state();
        ModuleContext ctx = MultiplayerPackTest.ctx(live);
        s.addScore(0, 7);
        s.addScore(2, 7);
        s.addScore(3, 9);
        s.setWinners(0b0111);
        assertEquals(RuleOutcome.WIN, VersusMultiplayer.INSTANCE.resolve(ctx, RuleOutcome.WIN));
        assertEquals(0b0101, s.winnersMask(), "seats 0 and 2 tie on 7; seat 3 was not a winner");
        s.setWinners(0b0010);
        assertEquals(RuleOutcome.WIN, VersusMultiplayer.INSTANCE.resolve(ctx, RuleOutcome.WIN));
        assertEquals(0b0010, s.winnersMask());
    }

    @Test
    void loseBecomesDraw() {
        HeadlessMatch match = match(TWO, 2, MultiplayerPackTest.survive(60, 1));
        match.state().damageSeat(0, 1);
        match.state().damageSeat(1, 1);
        MatchResult result = step(match);
        assertEquals(MatchResult.Outcome.DRAW, result.outcome(), "survive_time's LOSE has no loser in versus");
        assertEquals(0, result.winnersMask());

        HeadlessMatch live = match(TWO, 2, MultiplayerPackTest.survive(60, 1));
        ModuleContext ctx = MultiplayerPackTest.ctx(live);
        assertEquals(RuleOutcome.DRAW, VersusMultiplayer.INSTANCE.resolve(ctx, RuleOutcome.LOSE));
        assertEquals(0, live.state().winnersMask());
        assertEquals(RuleOutcome.DRAW, VersusMultiplayer.INSTANCE.resolve(ctx, RuleOutcome.DRAW));
        assertEquals(RuleOutcome.CONTINUE, VersusMultiplayer.INSTANCE.resolve(ctx, RuleOutcome.CONTINUE),
                "two seats alive: play on");
    }

    @Test
    void fourSeatResolveEndsOnOneAlive() {
        HeadlessMatch match = match(FOUR, 4, MultiplayerPackTest.survive(60, 1));
        MatchState s = match.state();
        assertEquals(4, s.seatCount());
        s.damageSeat(1, 1);
        s.damageSeat(3, 1);
        assertEquals(null, step(match), "two seats left: CONTINUE");
        assertEquals(0b0101, s.aliveSeatMask());
        s.damageSeat(0, 1);
        MatchResult result = step(match);
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(0b0100, result.winnersMask(), "seat 2 is the last one standing");
    }

    @Test
    void soloGetsRuntimeOpponent() {
        ResolvedGame game = MultiplayerPackTest.game(VersusMultiplayer.INSTANCE, TWO);
        HeadlessMatch match = MultiplayerPackTest.playing(game, 1, 5L);
        assertEquals(1, match.runtime().opponentSeat(), "versus fills seat 1 with the runtime opponent");
        MatchState s = match.state();
        assertEquals(2, s.seatCount());
        assertEquals(SeatLayout.shared(2, s.width(), s.height()), s.layout());
        assertTrue(s.pawnId(1) >= 0, "the opponent has a pawn");
        match.run(300, HeadlessMatch.InputScript.randomWalk());
        // and the opponent being alone ends it: seat 0 out → seat 1 wins
        if (match.result().isEmpty()) {
            s.damageSeat(0, 9);
            MatchResult result = step(match);
            assertEquals(MatchResult.Outcome.WIN, result.outcome());
            assertEquals(0b10, result.winnersMask());
        }
    }

    /**
     * Two human seats with different input logs: the run replays from its log to the same hash, and a fresh
     * run of the same scripts lands on the same hash again.
     */
    @Test
    void twoSeatAsymmetricInputsReplayToIdenticalHashes() {
        TestModules.install(); // versus is shipped (registered by the packs)
        ResolvedGame game = MultiplayerPackTest.game(VersusMultiplayer.INSTANCE, TWO);
        HeadlessMatch first = MultiplayerPackTest.playing(game, 2, 0xBEEFL).run(600, MultiplayerPackTest.asymmetric());
        MatchState s = first.state();
        assertEquals(-1, first.runtime().opponentSeat(), "two humans: no runtime opponent");
        assertTrue(s.pawnId(0) >= 0 && s.pawnId(1) >= 0);
        assertNotEquals(s.get(s.pawnId(0), za.co.neroland.neroarcade.runtime.Entities.X)
                + 1000 * s.get(s.pawnId(0), za.co.neroland.neroarcade.runtime.Entities.Y),
                s.get(s.pawnId(1), za.co.neroland.neroarcade.runtime.Entities.X)
                        + 1000 * s.get(s.pawnId(1), za.co.neroland.neroarcade.runtime.Entities.Y),
                "the seats went different ways");

        Replay replay = first.toReplay();
        assertEquals(first.hash(), replay.run().hash(), "replay from the input log");
        assertEquals(first.hash(), replay.run().hash(), "and again");
        HeadlessMatch second = MultiplayerPackTest.playing(game, 2, 0xBEEFL).run(600, MultiplayerPackTest.asymmetric());
        assertEquals(first.hash(), second.hash(), "a fresh run of the same scripts");
    }

    @Test
    void replayStable() {
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(VersusMultiplayer.INSTANCE, TWO), 2);
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(VersusMultiplayer.INSTANCE, FOUR), 4);
        MultiplayerPackTest.assertReplayStable(MultiplayerPackTest.game(VersusMultiplayer.INSTANCE, TWO), 1);
    }
}
