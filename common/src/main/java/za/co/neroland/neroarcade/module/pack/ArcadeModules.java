package za.co.neroland.neroarcade.module.pack;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.pack.arena.Grid16Arena;
import za.co.neroland.neroarcade.module.pack.control.FourWayControl;
import za.co.neroland.neroarcade.module.pack.multiplayer.SoloMultiplayer;
import za.co.neroland.neroarcade.module.pack.pace.SteadyPace;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.module.pack.theme.NeonVoidTheme;

/**
 * NeroArcade's shipped module pack. Called once from common init step 3, before the creative tab (chip items
 * are generated from the registry).
 *
 * <p><b>Invariants:</b> the seven empty-slot defaults register first, then the chips slot by slot in
 * {@code ModuleType} order; then the registry freezes and every default is verified, so a broken pack fails
 * at init, not at match time. Each {@code module.pack.<slot>} sub-package belongs to one work package and
 * registers through {@link #registerDefaults()} (defaults) or a per-slot method added here.
 */
public final class ArcadeModules {

    private ArcadeModules() {
    }

    /** Registers the pack, freezes the registry and verifies the defaults. Idempotent. */
    public static synchronized void init() {
        if (ModuleRegistry.isFrozen()) {
            return;
        }
        registerDefaults();
        ModuleRegistry.freeze();
        ModuleDefaults.verify();
    }

    /** The seven empty-slot defaults. Public so tests can build a registry from the shipped defaults. */
    public static void registerDefaults() {
        ModuleRegistry.register(Grid16Arena.INSTANCE);
        ModuleRegistry.register(CursorPawn.INSTANCE);
        ModuleRegistry.register(SurviveTimeRule.INSTANCE);
        ModuleRegistry.register(FourWayControl.INSTANCE);
        ModuleRegistry.register(SteadyPace.INSTANCE);
        ModuleRegistry.register(SoloMultiplayer.INSTANCE);
        ModuleRegistry.register(NeonVoidTheme.INSTANCE);
    }
}
