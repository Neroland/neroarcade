package za.co.neroland.neroarcade.module.pack.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;

class ControlPackTest {

    @BeforeEach
    void registry() {
        ModuleRegistry.resetForTests();
        ArcadeModules.registerDefaults();
        ControlPack.registerAll();
        ModuleRegistry.freeze();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void registersFourControlsSortedById() {
        List<String> ids = ModuleRegistry.byType(ModuleType.CONTROL).stream().map(m -> m.id().getPath()).toList();
        assertEquals(List.of("control/four_way", "control/one_button", "control/twin_stick", "control/two_way"), ids);
        assertSame(TwoWayControl.INSTANCE, ModuleRegistry.get(TwoWayControl.INSTANCE.id()).orElseThrow());
        assertSame(OneButtonControl.INSTANCE, ModuleRegistry.get(OneButtonControl.INSTANCE.id()).orElseThrow());
        assertSame(TwinStickControl.INSTANCE, ModuleRegistry.get(TwinStickControl.INSTANCE.id()).orElseThrow());
    }

    @Test
    void everyControlHasALegendAndFitsTheContract() {
        for (ArcadeModule module : ModuleRegistry.byType(ModuleType.CONTROL)) {
            ControlModule control = (ControlModule) module;
            PropertyValues defaults = PropertyValues.defaults(control);
            List<String> legend = control.legend(defaults);
            assertTrue(!legend.isEmpty() && legend.size() <= 2, module.id() + " legend " + legend);
            legend.forEach(key -> assertTrue(key.startsWith("neroarcade.legend."), key));
            assertTrue(module.properties().size() <= 3, module.id().toString());
            assertTrue(module.effectArgs(defaults).length <= 1, module.id().toString());
        }
    }
}
