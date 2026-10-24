package za.co.neroland.neroarcade.link;

import java.util.function.Supplier;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;

import za.co.neroland.neroarcade.platform.Services;

/**
 * NeroArcade's discovery entry (007 §1): module id {@code neroarcade}, schema 1, the five sections and the two
 * actions. The action list is static (the bridge does not filter it by config); the {@code library} section's
 * {@code actions} flags say which are switched on.
 */
final class ArcadeLinkInfo {

    private ArcadeLinkInfo() {
    }

    static LinkModuleInfo info() {
        return info(ArcadeLinkInfo::liveVersion);
    }

    static LinkModuleInfo info(Supplier<String> version) {
        String v;
        try {
            v = version.get();
        } catch (RuntimeException | LinkageError e) {
            v = null;
        }
        return new LinkModuleInfo(NeroArcadeLinkModule.MODULE_ID, v == null || v.isBlank() ? "unknown" : v,
                NeroArcadeLinkModule.SCHEMA_VERSION, NeroArcadeLinkModule.SECTIONS, NeroArcadeLinkModule.ACTIONS);
    }

    private static String liveVersion() {
        return Services.PLATFORM.getModVersion();
    }
}
