package za.co.neroland.neroarcade.module.pack;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.pack.arena.ArenaPack;
import za.co.neroland.neroarcade.module.pack.arena.Grid16Arena;
import za.co.neroland.neroarcade.module.pack.control.ControlPack;
import za.co.neroland.neroarcade.module.pack.control.FourWayControl;
import za.co.neroland.neroarcade.module.pack.hazard.HazardPack;
import za.co.neroland.neroarcade.module.pack.modifier.ModifierPack;
import za.co.neroland.neroarcade.module.pack.multiplayer.MultiplayerPack;
import za.co.neroland.neroarcade.module.pack.multiplayer.SoloMultiplayer;
import za.co.neroland.neroarcade.module.pack.pace.PacePack;
import za.co.neroland.neroarcade.module.pack.pace.SteadyPace;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;
import za.co.neroland.neroarcade.module.pack.pawn.PawnPack;
import za.co.neroland.neroarcade.module.pack.pickup.PickupPack;
import za.co.neroland.neroarcade.module.pack.rule.RulePack;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.module.pack.theme.NeonVoidTheme;
import za.co.neroland.neroarcade.module.pack.theme.ThemePack;

/**
 * NeroArcade's shipped module pack. Called once from common init step 3, before the creative tab (chip items
 * are generated from the registry).
 *
 * <p><b>Invariants:</b> the seven empty-slot defaults register first, then the chips slot by slot in
 * {@code ModuleType} order; then the registry freezes and every default is verified, so a broken pack fails
 * at init, not at match time. Each {@code module.pack.<slot>} sub-package belongs to one work package and
 * registers through {@link #registerDefaults()} (defaults) or its {@code <Slot>Pack.registerAll()} (the
 * non-default chips, alphabetical by id). 43 modules in total.
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
        registerPacks();
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

    /** The 36 non-default chips, slot by slot in {@code ModuleType} order. Public for test registries. */
    public static void registerPacks() {
        ArenaPack.registerAll();
        PawnPack.registerAll();
        RulePack.registerAll();
        HazardPack.registerAll();
        PickupPack.registerAll();
        ControlPack.registerAll();
        PacePack.registerAll();
        ModifierPack.registerAll();
        MultiplayerPack.registerAll();
        ThemePack.registerAll();
    }
}
