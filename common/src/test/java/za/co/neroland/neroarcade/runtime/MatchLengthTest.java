package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

class MatchLengthTest {

    @AfterEach
    void clear() {
        Budgets.clearTestOverrides();
    }

    @Test
    void ruleThatNeverDecidesEndsInDrawAtTheHardStop() {
        Budgets.overrideForTests(Budgets.Key.MAX_MATCH_SECONDS, 30);
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(TestModules.rule()), 1, 9L)
                .runToEnd(HeadlessMatch.InputScript.idle(), 10_000);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.DRAW, result.outcome());
        assertEquals(30 * 20, result.ticks());
        assertEquals(30 * 20, match.state().tick());
        assertEquals(30 * 20 + Budgets.COUNTDOWN_TICKS, match.runtime().steps());
        assertEquals(0, result.winnersMask());
    }
}
