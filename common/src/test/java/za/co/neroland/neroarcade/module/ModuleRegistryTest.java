package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PaceModule;
import za.co.neroland.neroarcade.module.pack.pace.SteadyPace;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;

class ModuleRegistryTest {

    /** A pace stub with a configurable id and declared type. */
    private static PaceModule pace(String path, ModuleType declared) {
        return new PaceModule() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("neroarcade", path);
            }

            @Override
            public ModuleType type() {
                return declared;
            }

            @Override
            public List<PropertySpec> properties() {
                return List.of();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of();
            }

            @Override
            public int divisor(ModuleContext ctx) {
                return 1;
            }
        };
    }

    @BeforeEach
    void clear() {
        TestModules.reset();
    }

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void registerThenGet() {
        ModuleRegistry.register(SteadyPace.INSTANCE);
        assertSame(SteadyPace.INSTANCE, ModuleRegistry.get(SteadyPace.INSTANCE.id()).orElseThrow());
        assertSame(SteadyPace.INSTANCE, ModuleRegistry.get(SteadyPace.INSTANCE.id(), PaceModule.class).orElseThrow());
        assertTrue(ModuleRegistry.get(SteadyPace.INSTANCE.id(), za.co.neroland.neroarcade.module.hook.PawnModule.class).isEmpty());
        assertTrue(ModuleRegistry.get(TestModules.id("pace/missing")).isEmpty());
    }

    @Test
    void duplicateIdThrows() {
        ModuleRegistry.register(SteadyPace.INSTANCE);
        assertThrows(IllegalStateException.class, () -> ModuleRegistry.register(SteadyPace.INSTANCE));
    }

    @Test
    void typeHookMismatchThrows() {
        assertThrows(IllegalStateException.class, () -> ModuleRegistry.register(pace("arena/odd", ModuleType.ARENA)));
    }

    @Test
    void pathMustStartWithSlotKey() {
        assertThrows(IllegalStateException.class, () -> ModuleRegistry.register(pace("pawn/odd", ModuleType.PACE)));
        assertThrows(IllegalStateException.class, () -> ModuleRegistry.register(pace("pace/9bad", ModuleType.PACE)));
        assertThrows(IllegalStateException.class, () -> ModuleRegistry.register(pace("pace/", ModuleType.PACE)));
    }

    @Test
    void frozenRegistryRefusesRegistration() {
        ModuleRegistry.register(SteadyPace.INSTANCE);
        ModuleRegistry.freeze();
        ModuleRegistry.freeze();
        assertTrue(ModuleRegistry.isFrozen());
        assertThrows(IllegalStateException.class, () -> ModuleRegistry.register(CursorPawn.INSTANCE));
    }

    @Test
    void byTypeSortedImmutableAndAllIsTheSum() {
        ModuleRegistry.register(pace("pace/zeta", ModuleType.PACE));
        ModuleRegistry.register(SteadyPace.INSTANCE);
        ModuleRegistry.register(pace("pace/alpha", ModuleType.PACE));
        ModuleRegistry.register(CursorPawn.INSTANCE);
        ModuleRegistry.freeze();
        List<ArcadeModule> paces = ModuleRegistry.byType(ModuleType.PACE);
        assertEquals(List.of("pace/alpha", "pace/steady", "pace/zeta"),
                paces.stream().map(m -> m.id().getPath()).toList());
        assertThrows(UnsupportedOperationException.class, () -> paces.add(SteadyPace.INSTANCE));
        int sum = 0;
        for (ModuleType type : ModuleType.values()) {
            sum += ModuleRegistry.byType(type).size();
        }
        assertEquals(sum, ModuleRegistry.all().size());
        assertEquals(4, ModuleRegistry.size());
    }
}
