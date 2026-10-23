package za.co.neroland.neroarcade.module.pack.rule;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The shipped RULE chips other than the default ({@link SurviveTimeRule}, registered by
 * {@code ArcadeModules.registerDefaults()}), in alphabetical id order.
 */
public final class RulePack {

    private RulePack() {
    }

    public static void registerAll() {
        ModuleRegistry.register(ClearBoardRule.INSTANCE);
        ModuleRegistry.register(LastStandingRule.INSTANCE);
        ModuleRegistry.register(ReachExitRule.INSTANCE);
        ModuleRegistry.register(ScoreTargetRule.INSTANCE);
    }
}
