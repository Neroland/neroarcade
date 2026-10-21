package za.co.neroland.neroarcade.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.GameBenchMenu;

/**
 * Menu types. A plain vanilla {@code MenuType} (no extra open data): the client copy needs no bench position, which
 * arrives with the first {@code BenchStatePayload}.
 */
public final class ModMenus {

    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroArcadeCommon.MOD_ID);

    public static final RegistryEntry<MenuType<GameBenchMenu>> GAME_BENCH = MENUS.register(ArcadeIds.GAME_BENCH,
            key -> new MenuType<>(GameBenchMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
