package za.co.neroland.neroarcade.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.GameBenchMenu;
import za.co.neroland.neroarcade.duplicator.DuplicatorMenu;
import za.co.neroland.neroarcade.exchange.ExchangeMenu;

/**
 * Menu types. A plain vanilla {@code MenuType} (no extra open data): the client copy needs no bench position, which
 * arrives with the first {@code BenchStatePayload}.
 */
public final class ModMenus {

    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroArcadeCommon.MOD_ID);

    public static final RegistryEntry<MenuType<GameBenchMenu>> GAME_BENCH = MENUS.register(ArcadeIds.GAME_BENCH,
            key -> new MenuType<>(GameBenchMenu::new, FeatureFlags.VANILLA_SET));

    /** "Copy a game" — the Cartridge Duplicator. */
    public static final RegistryEntry<MenuType<DuplicatorMenu>> CARTRIDGE_DUPLICATOR = MENUS.register(
            ArcadeIds.CARTRIDGE_DUPLICATOR, key -> new MenuType<>(DuplicatorMenu::new, FeatureFlags.VANILLA_SET));

    /** "Find a game to download" — the Arcade Exchange; pages arrive as {@code ExchangePagePayload}. */
    public static final RegistryEntry<MenuType<ExchangeMenu>> ARCADE_EXCHANGE = MENUS.register(
            ArcadeIds.ARCADE_EXCHANGE, key -> new MenuType<>(ExchangeMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
