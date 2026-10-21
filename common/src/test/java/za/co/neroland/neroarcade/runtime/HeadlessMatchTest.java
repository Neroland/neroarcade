package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.TestModules;

class HeadlessMatchTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    @Test
    void defaultChipsCartridgePlaysToTheTimer() throws ValidationError {
        GameDef def = CartridgeValidator.decodeJson(TestFiles.cartridge("default_survive.json"), Budgets.MAX_IMPORT_JSON_BYTES);
        HeadlessMatch match = HeadlessMatch.of(def, 1, 0x4E45524FL);
        int[] entitiesArray = {System.identityHashCode(match.state().entities)};
        match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.randomWalk());
        assertEquals(MatchPhase.PLAYING, match.state().phase());
        match.runToEnd(HeadlessMatch.InputScript.randomWalk(), Budgets.maxMatchSeconds() * 20);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome(), "no hazard: survive_time is won by the timer");
        assertEquals(60 * 20, result.ticks());
        assertEquals(1, result.winnersMask());
        assertEquals(match.hash(), result.finalHash());
        assertEquals(entitiesArray[0], System.identityHashCode(match.state().entities), "entity array reallocated");
        assertTrue(match.state().phase() == MatchPhase.ENDED);
        assertSame(match.state(), match.runtime().state());
    }

    @Test
    void endedMatchIgnoresFurtherSteps() throws ValidationError {
        GameDef def = CartridgeValidator.decodeJson(TestFiles.cartridge("default_survive.json"), Budgets.MAX_IMPORT_JSON_BYTES);
        HeadlessMatch match = HeadlessMatch.of(def, 1, 1L).runToEnd(HeadlessMatch.InputScript.idle(), 5000);
        long hash = match.hash();
        match.run(50, HeadlessMatch.InputScript.randomWalk());
        assertEquals(hash, match.hash());
    }
}
