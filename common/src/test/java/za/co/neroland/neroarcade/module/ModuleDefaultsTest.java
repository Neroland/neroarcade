package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.pack.pace.SteadyPace;

class ModuleDefaultsTest {

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void everyDefaultResolvesInTheShippedRegistry() {
        TestModules.installShipped();
        for (ModuleType type : ModuleType.values()) {
            var id = ModuleDefaults.defaultFor(type);
            if (type == ModuleType.HAZARD || type == ModuleType.PICKUP || type == ModuleType.MODIFIER) {
                assertTrue(id.isEmpty(), type.name());
            } else {
                assertTrue(ModuleRegistry.get(id.orElseThrow(), ModuleRegistry.hookOf(type)).isPresent(), type.name());
            }
        }
    }

    @Test
    void defaultIdsAreTheContractsIds() {
        Map<ModuleType, String> expected = Map.of(
                ModuleType.ARENA, "neroarcade:arena/grid_16",
                ModuleType.PAWN, "neroarcade:pawn/cursor",
                ModuleType.RULE, "neroarcade:rule/survive_time",
                ModuleType.CONTROL, "neroarcade:control/four_way",
                ModuleType.PACE, "neroarcade:pace/steady",
                ModuleType.MULTIPLAYER, "neroarcade:multiplayer/solo",
                ModuleType.THEME, "neroarcade:theme/neon_void");
        expected.forEach((type, id) -> assertEquals(id, ModuleDefaults.defaultFor(type).orElseThrow().toString()));
    }

    @Test
    void missingDefaultFailsVerification() {
        TestModules.reset();
        ModuleRegistry.register(SteadyPace.INSTANCE);
        ModuleRegistry.freeze();
        assertThrows(IllegalStateException.class, ModuleDefaults::verify);
    }
}
