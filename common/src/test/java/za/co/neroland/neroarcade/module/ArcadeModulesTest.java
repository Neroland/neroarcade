package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 008 §6.1: the shipped pack is the seven defaults plus the 36 Stage 3 chips, 43 modules in all. */
class ArcadeModulesTest {

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void shipsFortyThreeModules() {
        TestModules.installShipped();
        assertEquals(43, ModuleRegistry.size());
        Map<ModuleType, List<String>> expected = new EnumMap<>(ModuleType.class);
        expected.put(ModuleType.ARENA, List.of("grid_16", "grid_32", "ring_24", "strip_9"));
        expected.put(ModuleType.PAWN, List.of("cursor", "paddle", "runner", "serpent", "ship", "stacker"));
        expected.put(ModuleType.RULE, List.of("clear_board", "last_standing", "reach_exit", "score_target",
                "survive_time"));
        expected.put(ModuleType.HAZARD, List.of("bouncing_ball", "chaser", "falling_blocks", "laser_sweep",
                "shrinking_floor"));
        expected.put(ModuleType.PICKUP, List.of("key", "score_orb", "shield", "speed_boost"));
        expected.put(ModuleType.CONTROL, List.of("four_way", "one_button", "twin_stick", "two_way"));
        expected.put(ModuleType.PACE, List.of("pulse", "ramp", "steady"));
        expected.put(ModuleType.MODIFIER, List.of("fog", "low_gravity", "mirror", "wrap_around"));
        expected.put(ModuleType.MULTIPLAYER, List.of("coop", "solo", "turns", "versus"));
        expected.put(ModuleType.THEME, List.of("cindara_dusk", "hangar", "neon_void", "terminal_green"));
        for (ModuleType type : ModuleType.values()) {
            List<String> names = ModuleRegistry.byType(type).stream()
                    .map(m -> m.id().getPath().substring(type.key().length() + 1)).toList();
            assertEquals(expected.get(type), names, type.toString());
        }
    }

    @Test
    void initIsIdempotentAndVerifiesTheDefaults() {
        TestModules.installShipped();
        za.co.neroland.neroarcade.module.pack.ArcadeModules.init();
        assertEquals(43, ModuleRegistry.size());
        ModuleDefaults.verify();
    }
}
