package za.co.neroland.neroarcade.module.pack.theme;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The THEME slot's non-default chips. The default ({@link NeonVoidTheme}) registers in
 * {@code ArcadeModules.registerDefaults()}; these follow in alphabetical id order.
 */
public final class ThemePack {

    private ThemePack() {
    }

    /** Registers {@code cindara_dusk}, {@code hangar}, {@code terminal_green}. Call before the registry freezes. */
    public static void registerAll() {
        ModuleRegistry.register(CindaraDuskTheme.INSTANCE);
        ModuleRegistry.register(HangarTheme.INSTANCE);
        ModuleRegistry.register(TerminalGreenTheme.INSTANCE);
    }
}
