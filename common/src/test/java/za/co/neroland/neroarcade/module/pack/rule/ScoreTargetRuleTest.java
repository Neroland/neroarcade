package za.co.neroland.neroarcade.module.pack.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;

class ScoreTargetRuleTest {

    private static HeadlessMatch playing(ResolvedGame game, int seats, long seed) {
        return HeadlessMatch.of(game, seats, seed).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    @Test
    void winsAtTarget() {
        HeadlessMatch match = playing(TestModules.resolvedOf(ScoreTargetRule.INSTANCE), 1, 11L);
        match.step(SeatInput.NONE);
        assertTrue(match.result().isEmpty(), "no score yet");
        match.state().addScore(0, 99);
        match.step(SeatInput.NONE);
        assertTrue(match.result().isEmpty(), "99 is short of 100");
        match.state().addScore(0, 1);
        match.step(SeatInput.NONE);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(1, result.winnersMask());
    }

    @Test
    void loseWhenAllDead() {
        HeadlessMatch match = playing(TestModules.resolvedOf(ScoreTargetRule.INSTANCE), 1, 12L);
        assertEquals(3, match.state().lives(0), "lives 3 by default");
        assertEquals(0, match.state().timerTicks(), "no clock");
        match.state().damageSeat(0, 3);
        match.step(SeatInput.NONE);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.LOSE, result.outcome());
        assertEquals(0, result.winnersMask());
    }

    @Test
    void noScoreSourceRunsToHardStop() {
        int hardStop = Budgets.maxMatchSeconds() * Budgets.TICKS_PER_SECOND;
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(ScoreTargetRule.INSTANCE), 1, 13L)
                .runToEnd(HeadlessMatch.InputScript.idle(), Budgets.COUNTDOWN_TICKS + hardStop + 20);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.DRAW, result.outcome());
        assertEquals(0, result.winnersMask());
        assertEquals(hardStop, result.ticks());
        assertEquals(RuleOutcome.CONTINUE, ScoreTargetRule.INSTANCE.evaluate(
                new ModuleContext(match.state(), PropertyValues.defaults(ScoreTargetRule.INSTANCE), null,
                        match.runtime().game(), match.state().tick(), 0)),
                "the rule itself never decides without points or deaths");
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(ScoreTargetRule.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1001L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1001L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
