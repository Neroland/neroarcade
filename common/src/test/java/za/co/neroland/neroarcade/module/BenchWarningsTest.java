package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;
import za.co.neroland.neroarcade.module.pack.arena.Grid16Arena;
import za.co.neroland.neroarcade.module.pack.control.FourWayControl;
import za.co.neroland.neroarcade.module.pack.multiplayer.SoloMultiplayer;
import za.co.neroland.neroarcade.module.pack.pace.SteadyPace;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.module.pack.theme.NeonVoidTheme;

/** Each row of the contract's odd-combination table raises exactly its warning, derived from flags alone. */
class BenchWarningsTest {

    /** A lethal hazard so the default survival rule does not also raise NO_THREAT. */
    private static ResolvedGame withThreat(ArcadeModule... modules) {
        ArcadeModule[] all = new ArcadeModule[modules.length + 1];
        System.arraycopy(modules, 0, all, 0, modules.length);
        all[modules.length] = TestModules.hazard(ModuleFlag.LETHAL);
        return TestModules.resolvedOf(all);
    }

    private static void assertWarnings(ResolvedGame game, BenchWarning... expected) {
        assertEquals(List.of(expected), BenchWarnings.evaluate(game));
    }

    @Test
    void saneCombinationHasNoWarning() {
        assertWarnings(withThreat());
    }

    @Test
    void eachTableRowRaisesExactlyItsWarning() {
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.NEEDS_SCORE)), BenchWarning.SCORE_RULE_WITHOUT_SCORE_SOURCE);
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.NEEDS_SCORE), TestModules.pickup(ModuleFlag.SCORES)));
        // 008 §8 item 2: a HAZARD (bouncing_ball on goals) or PAWN (stacker rows) declaring SCORES is a source too
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.NEEDS_SCORE), TestModules.hazard(ModuleFlag.SCORES)));
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.NEEDS_SCORE), TestModules.pawn(ModuleFlag.SCORES)));
        assertWarnings(withThreat(TestModules.pickup(ModuleFlag.IS_KEY)), BenchWarning.KEY_WITHOUT_EXIT);
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.PLACES_EXIT), TestModules.pawn(ModuleFlag.ONE_AXIS)),
                BenchWarning.EXIT_ON_ONE_AXIS);
        assertWarnings(withThreat(TestModules.control(ModuleFlag.NEEDS_AIM)), BenchWarning.AIM_WITHOUT_FIRING_PAWN);
        assertWarnings(withThreat(TestModules.control(ModuleFlag.NEEDS_AIM), TestModules.pawn(ModuleFlag.FIRES)));
        assertWarnings(withThreat(TestModules.control(ModuleFlag.ONE_AXIS, ModuleFlag.BUTTONS_ONLY)),
                BenchWarning.ONE_BUTTON_FREE_PAWN);
        assertWarnings(withThreat(TestModules.control(ModuleFlag.ONE_AXIS, ModuleFlag.BUTTONS_ONLY),
                TestModules.pawn(ModuleFlag.AUTO_MOVE)));
        assertWarnings(withThreat(TestModules.control(ModuleFlag.ONE_AXIS)), BenchWarning.TWO_WAY_ON_FREE_PAWN);
        assertWarnings(withThreat(TestModules.control(ModuleFlag.ONE_AXIS), TestModules.pawn(ModuleFlag.ONE_AXIS)));
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.NEEDS_CLEARABLE)), BenchWarning.CLEAR_BOARD_WITHOUT_PICKUPS);
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.MULTI_SEAT_ONLY)), BenchWarning.VERSUS_NEEDS_SEATS);
        assertWarnings(withThreat(TestModules.rule(ModuleFlag.MULTI_SEAT_ONLY),
                TestModules.multiplayer(true, ModuleFlag.MULTI_SEAT_ONLY, ModuleFlag.RUNTIME_OPPONENT)));
        assertWarnings(TestModules.resolvedOf(), BenchWarning.NO_THREAT);
        assertWarnings(TestModules.resolvedOf(TestModules.pawn(ModuleFlag.AUTO_MOVE),
                TestModules.hazard(ModuleFlag.LETHAL), TestModules.hazard(ModuleFlag.LETHAL),
                TestModules.hazard(ModuleFlag.LETHAL)), BenchWarning.CROWDED);
        assertWarnings(TestModules.resolvedOf(true, TestModules.hazard(ModuleFlag.LETHAL)), BenchWarning.UNKNOWN_PROPERTY);
    }

    @Test
    void neverThrowsForAnyCombinationOfDefaults() {
        ArcadeModule[] defaults = {Grid16Arena.INSTANCE, CursorPawn.INSTANCE, SurviveTimeRule.INSTANCE,
                FourWayControl.INSTANCE, SteadyPace.INSTANCE, SoloMultiplayer.INSTANCE, NeonVoidTheme.INSTANCE};
        for (int mask = 0; mask < (1 << defaults.length); mask++) {
            java.util.List<ArcadeModule> chosen = new java.util.ArrayList<>();
            for (int i = 0; i < defaults.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    chosen.add(defaults[i]);
                }
            }
            BenchWarnings.evaluate(TestModules.resolvedOf(chosen.toArray(new ArcadeModule[0])));
        }
        ArcadeModules.class.getName(); // the defaults above are the shipped pack's
    }
}
