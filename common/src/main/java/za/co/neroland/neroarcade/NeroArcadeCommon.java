package za.co.neroland.neroarcade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neroarcade.board.Boards;
import za.co.neroland.neroarcade.compat.Seams;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.exchange.Exchange;
import za.co.neroland.neroarcade.link.NeroArcadeLinkModule;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;
import za.co.neroland.neroarcade.network.ArcadeNetwork;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.progression.QuestHooks;
import za.co.neroland.neroarcade.registry.ModBlockEntities;
import za.co.neroland.neroarcade.registry.ModBlocks;
import za.co.neroland.neroarcade.registry.ModCreativeTab;
import za.co.neroland.neroarcade.registry.ModDataComponents;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModMenus;
import za.co.neroland.neroarcade.registry.ModSounds;
import za.co.neroland.neroarcade.server.ArcadeServer;
import za.co.neroland.neroarcade.telemetry.NeroArcadeTelemetry;

/**
 * Loader-agnostic entry point for NeroArcade. Each loader entry point (Fabric / Forge / NeoForge)
 * calls {@link #init()} once during mod construction, then wires its own networking and event hooks.
 *
 * <p>The ordering below is not cosmetic. Fabric registers <em>eagerly</em> — the moment a registry
 * class is touched — so anything that must exist before something else has to be listed before it, on
 * every loader. The numbered steps are the Neroland convention; later work fills the reserved slots
 * rather than inserting itself wherever is convenient.
 */
public final class NeroArcadeCommon {

    public static final String MOD_ID = "neroarcade";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroArcade");

    private NeroArcadeCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroArcade] common init");

        // 0. Platform seams (PlatformInfo + NetworkPlatform), resolved here during construction and
        //    never lazily on a tick path — a late ServiceLoader read can throw
        //    ServiceConfigurationError out of gameplay code (Nerospace crash precedent MC-NEROSPACE-F).
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroArcadeConfig.init();

        // 2. Anonymous, NeroArcade-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default (opt-out via
        //    telemetryEnabled=false); stays inert while the build's DSN is the placeholder
        //    (see NeroArcadeTelemetry's PLACEHOLDER_DSN guard).
        NeroArcadeTelemetry.init();

        // 3. Content registries. The module registry first: chip items (Stage 2) are generated from it, so
        //    it must be complete and frozen before anything lists them; a missing default module fails
        //    here, at init, never at match time. Then items (Fabric registers eagerly), blocks, block
        //    entities, menus and entities. NeroArcade's own creative tab comes last, after every
        //    contributor has queued its items.
        //    Order: data components (items carry them) -> items -> blocks -> block entities -> menus -> sounds ->
        //    tab. Items reference their blocks lazily, so either load order is safe on every loader.
        ArcadeModules.init();
        ModDataComponents.init();
        ModItems.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModSounds.init();
        ModCreativeTab.init();

        // 4. Player-data erasure registration. Registered before any store can exist on purpose:
        //    registering late is how an erasure request silently misses a store (POPIA/GDPR). Every
        //    player-keyed store adds itself to NeroArcadeData and reads its SavedData through Core's
        //    SavedDataRecovery. Stage 4A: the leaderboards and the credit opt-in (Boards adds both, installs the
        //    bench's credit resolver and the hourly retention sweep).
        NeroArcadeData.init();
        Boards.init();
        // Stage 4B: the Arcade Exchange (listings + drafts stores, credit stripping, the hourly drafts sweep).
        Exchange.init();
        // Stage 6: the loaded Game Benches' draft authors and the privacy-export files (+ their cooldown and the
        //    export retention sweep).
        za.co.neroland.neroarcade.privacy.ArcadePrivacy.init();

        // 5. Compat detection — resolved once here, never per interaction (003 §10): the coin-op bridge
        //    (NeroEconomy, through META-INF/services, only when it is loaded), the no-op seams, and Core's palette.
        Seams.init(Services.PLATFORM::isModLoaded);

        // 6. Declare the payloads on NeroArcade's own neroarcade:main channel before any loader
        //    registers them: every loader entry point runs this method first, then wires its own
        //    networking (on Forge the channel is sealed at build()). Seven payloads (Stage 2).
        ArcadeNetwork.init();

        // 7. Server hooks: seats release on logout; runtime registries clear when the server stops. Quest-pack
        //    hooks and other Core progression listeners join this slot later.
        ArcadeServer.init();
        QuestHooks.init();

        // 8. The NeroLink module (007) goes LAST and swallows its own failures (RuntimeException | LinkageError,
        //    one warning) — a broken companion integration must never take the mod down with it.
        NeroArcadeLinkModule.init();
    }
}
