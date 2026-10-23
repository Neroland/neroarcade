package za.co.neroland.neroarcade.module.pack.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;

class LastStandingRuleTest {

    private static ResolvedGame withRuleProps(ResolvedGame g, PropertyValues p) {
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(), g.pawnProps(), g.rule(), p,
                g.hazards(), g.hazardProps(), g.pickups(), g.pickupProps(), g.control(), g.controlProps(), g.pace(),
                g.paceProps(), g.modifier(), g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(),
                g.themeProps(), g.unknownProperties());
    }

    private static PropertyValues soloSeconds(int seconds) throws ValidationError {
        return PropertyValues.defaults(LastStandingRule.INSTANCE).with("solo_seconds", new PropertyValue.IntValue(seconds));
    }

    /** Two human seats, no runtime opponent (a test multiplayer chip asking for 2 seats). */
    private static HeadlessMatch duel(ResolvedGame game, long seed) {
        return HeadlessMatch.of(game, 2, seed).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static ResolvedGame duelGame() {
        return TestModules.resolvedOf(LastStandingRule.INSTANCE, TestModules.multiplayer(false));
    }

    @Test
    void lastAliveWins() {
        HeadlessMatch match = duel(duelGame(), 41L);
        assertEquals(2, match.state().seatCount());
        assertEquals(0, match.state().timerTicks(), "no clock in a duel");
        match.step(SeatInput.NONE, SeatInput.NONE);
        assertTrue(match.result().isEmpty());
        match.state().damageSeat(1, 1);
        match.step(SeatInput.NONE, SeatInput.NONE);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(1, result.winnersMask());
    }

    @Test
    void allDeadDraws() {
        HeadlessMatch match = duel(duelGame(), 42L);
        match.state().damageSeat(0, 1);
        match.state().damageSeat(1, 1);
        match.step(SeatInput.NONE, SeatInput.NONE);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.DRAW, result.outcome());
        assertEquals(0, result.winnersMask());
    }

    @Test
    void soloFallbackUsesTimer() throws ValidationError {
        // the default solo chip offers no runtime opponent, so a one-seat start falls back to the solo clock
        ResolvedGame game = withRuleProps(TestModules.resolvedOf(LastStandingRule.INSTANCE), soloSeconds(10));
        HeadlessMatch match = HeadlessMatch.of(game, 1, 43L);
        assertEquals(1, match.state().seatCount());
        assertEquals(200, match.state().timerTicks());
        match.runToEnd(HeadlessMatch.InputScript.idle(), Budgets.COUNTDOWN_TICKS + 400);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(1, result.winnersMask());
        assertEquals(200, result.ticks());
    }

    @Test
    void dropLeavesSurvivorOnClock() throws ValidationError {
        ResolvedGame game = withRuleProps(duelGame(), soloSeconds(10));
        HeadlessMatch match = duel(game, 44L);
        match.run(20, HeadlessMatch.InputScript.idle());
        assertEquals(0, match.state().timerTicks());
        match.runtime().dropSeat(1);
        assertEquals(200, match.state().timerTicks());
        match.step(SeatInput.NONE, SeatInput.NONE);
        assertTrue(match.result().isEmpty(), "the survivor plays out the clock");
        match.runToEnd(HeadlessMatch.InputScript.idle(), 400);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(1, result.winnersMask(), "the dropped seat never wins");
        assertEquals(220, result.ticks());
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(LastStandingRule.INSTANCE, TestModules.multiplayer(false),
                TestChips.Bouncer.INSTANCE);
        long a = HeadlessMatch.of(game, 2, 1004L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 2, 1004L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
