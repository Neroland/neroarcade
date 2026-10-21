package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;

/** Every "makes no sense" combination of the contract's table still runs and still ends. */
class OddCombinationRunsTest {

    @BeforeEach
    void shortMatches() {
        Budgets.overrideForTests(Budgets.Key.MAX_MATCH_SECONDS, 30);
    }

    @AfterEach
    void clear() {
        Budgets.clearTestOverrides();
    }

    @Test
    void everyTableCombinationRunsToATerminalPhase() {
        Map<String, ResolvedGame> combos = new LinkedHashMap<>();
        combos.put("score rule, no score source", TestModules.resolvedOf(TestModules.rule(ModuleFlag.NEEDS_SCORE),
                TestChips.Bouncer.INSTANCE));
        combos.put("key without exit", TestModules.resolvedOf(TestModules.pickup(ModuleFlag.IS_KEY)));
        combos.put("exit on one axis", TestModules.resolvedOf(TestModules.rule(ModuleFlag.PLACES_EXIT),
                TestModules.pawn(ModuleFlag.ONE_AXIS)));
        combos.put("aim without firing", TestModules.resolvedOf(TestModules.control(ModuleFlag.NEEDS_AIM)));
        combos.put("one button, free pawn", TestModules.resolvedOf(
                TestModules.control(ModuleFlag.ONE_AXIS, ModuleFlag.BUTTONS_ONLY)));
        combos.put("two way, free pawn", TestModules.resolvedOf(TestModules.control(ModuleFlag.ONE_AXIS)));
        combos.put("clear board, no pickups", TestModules.resolvedOf(TestModules.rule(ModuleFlag.NEEDS_CLEARABLE)));
        combos.put("versus with one seat, runtime opponent", TestModules.resolvedOf(
                TestModules.rule(ModuleFlag.MULTI_SEAT_ONLY), TestChips.Versus.INSTANCE,
                TestModules.pawn(ModuleFlag.LETHAL_TO_PAWNS)));
        combos.put("versus with one seat, solo fallback", TestModules.resolvedOf(
                TestModules.rule(ModuleFlag.MULTI_SEAT_ONLY), TestModules.multiplayer(false, ModuleFlag.MULTI_SEAT_ONLY)));
        combos.put("survive with no threat", TestModules.resolvedOf());
        combos.put("crowded", TestModules.resolvedOf(TestModules.pawn(ModuleFlag.AUTO_MOVE), TestChips.Bouncer.INSTANCE,
                TestChips.Bouncer.INSTANCE, TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE));
        combos.put("everything stacked", TestModules.resolvedOf(TestChips.Bouncer.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE, TestChips.Orb.INSTANCE, TestChips.Orb.INSTANCE,
                TestChips.Mirror.INSTANCE, TestChips.Coop.INSTANCE, TestChips.ScoreRule.INSTANCE));
        combos.forEach((name, game) -> {
            HeadlessMatch match = HeadlessMatch.of(game, 1, name.hashCode())
                    .runToEnd(HeadlessMatch.InputScript.randomWalk(), 1200);
            assertEquals(MatchPhase.ENDED, match.state().phase(), name);
        });
    }

    @Test
    void runtimeOpponentFillsSeatOne() {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(TestModules.rule(ModuleFlag.MULTI_SEAT_ONLY),
                TestChips.Versus.INSTANCE), 1, 5L);
        assertEquals(2, match.state().seatCount());
        assertEquals(1, match.runtime().opponentSeat());
        match.run(200, HeadlessMatch.InputScript.idle());
        int pawn = match.state().pawnId(1);
        int x = match.state().get(pawn, Entities.X);
        int y = match.state().get(pawn, Entities.Y);
        int start = (14 << 8) + 128;
        assertEquals(true, x != start || y != start, "the runtime opponent moves");
        assertTrue(match.log().entries().stream().noneMatch(e -> e.seat() == match.runtime().opponentSeat()),
                "the harness never scripts or logs the runtime opponent's seat");
    }
}
