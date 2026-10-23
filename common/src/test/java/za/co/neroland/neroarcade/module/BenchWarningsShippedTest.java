package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.pack.control.OneButtonControl;
import za.co.neroland.neroarcade.module.pack.hazard.BouncingBallHazard;
import za.co.neroland.neroarcade.module.pack.hazard.ChaserHazard;
import za.co.neroland.neroarcade.module.pack.hazard.FallingBlocksHazard;
import za.co.neroland.neroarcade.module.pack.hazard.ShrinkingFloorHazard;
import za.co.neroland.neroarcade.module.pack.multiplayer.TurnsMultiplayer;
import za.co.neroland.neroarcade.module.pack.multiplayer.VersusMultiplayer;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;
import za.co.neroland.neroarcade.module.pack.pawn.RunnerPawn;
import za.co.neroland.neroarcade.module.pack.pawn.SerpentPawn;
import za.co.neroland.neroarcade.module.pack.pickup.KeyPickup;
import za.co.neroland.neroarcade.module.pack.rule.ReachExitRule;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.runtime.Budgets;

/** 008 §6.5: the bench warnings real chips raise, on the example cartridges and on the table's odd rows. */
class BenchWarningsShippedTest {

    private static final List<String> STAGE_1_STUBS = List.of("coop_mirror", "default_survive", "orb_dodge");

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    private static ResolvedGame cartridge(String name) throws Exception {
        return CartridgeValidator.validate(CartridgeValidator.decodeJson(TestFiles.cartridge(name + ".json"),
                Budgets.MAX_IMPORT_JSON_BYTES));
    }

    @Test
    void exampleCartridgesRaiseOnlyTheHotSeatWarning() throws Exception {
        for (Path file : TestFiles.cartridges()) {
            String name = file.getFileName().toString().replace(".json", "");
            if (STAGE_1_STUBS.contains(name)) {
                continue;
            }
            // turns has no runtime opponent, so a hot-seat cartridge on the bench says it needs a second seat
            List<BenchWarning> expected = name.equals("hot_seat_orbs") ? List.of(BenchWarning.VERSUS_NEEDS_SEATS)
                    : List.of();
            assertEquals(expected, BenchWarnings.evaluate(cartridge(name)), name);
        }
    }

    @Test
    void paddleDuelWithAScoreRuleHasASource() throws Exception {
        GameDef duel = CartridgeValidator.decodeJson(TestFiles.cartridge("paddle_duel.json"), Budgets.MAX_IMPORT_JSON_BYTES);
        GameDef scored = duel.withSlot(ModuleType.RULE, List.of(SlotEntry.of(TestModules.id("rule/score_target"))));
        // 008 §8 item 2 landed: bouncing_ball declares SCORES, so the score rule has a source
        assertEquals(List.of(), BenchWarnings.evaluate(CartridgeValidator.validate(scored)));
    }

    @Test
    void oddRowsWithRealChips() {
        assertEquals(List.of(BenchWarning.ONE_BUTTON_FREE_PAWN), BenchWarnings.evaluate(TestModules.resolvedOf(
                CursorPawn.INSTANCE, OneButtonControl.INSTANCE, FallingBlocksHazard.INSTANCE)));
        assertEquals(List.of(BenchWarning.EXIT_ON_ONE_AXIS), BenchWarnings.evaluate(TestModules.resolvedOf(
                RunnerPawn.INSTANCE, ReachExitRule.INSTANCE)));
        assertEquals(List.of(BenchWarning.KEY_WITHOUT_EXIT), BenchWarnings.evaluate(TestModules.resolvedOf(
                KeyPickup.INSTANCE, FallingBlocksHazard.INSTANCE)));
        assertEquals(List.of(), BenchWarnings.evaluate(TestModules.resolvedOf(
                VersusMultiplayer.INSTANCE, FallingBlocksHazard.INSTANCE)), "versus brings a runtime opponent");
        assertEquals(List.of(BenchWarning.VERSUS_NEEDS_SEATS), BenchWarnings.evaluate(TestModules.resolvedOf(
                TurnsMultiplayer.INSTANCE, FallingBlocksHazard.INSTANCE)));
        assertEquals(List.of(), BenchWarnings.evaluate(TestModules.resolvedOf(
                SurviveTimeRule.INSTANCE, ShrinkingFloorHazard.INSTANCE)), "shrinking_floor is LETHAL");
        assertEquals(List.of(BenchWarning.CROWDED), BenchWarnings.evaluate(TestModules.resolvedOf(
                SerpentPawn.INSTANCE, ChaserHazard.INSTANCE, FallingBlocksHazard.INSTANCE,
                BouncingBallHazard.INSTANCE)));
    }
}
