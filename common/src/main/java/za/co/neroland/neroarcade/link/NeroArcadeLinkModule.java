package za.co.neroland.neroarcade.link;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.progression.ArcadeMatchEnds;
import za.co.neroland.neroarcade.server.ServerHooks;

/**
 * NeroArcade's module for Neroland Core's link API, read by the NeroLink bridge and its companion app (007).
 *
 * <ul>
 *   <li><b>Sections</b> ({@link ArcadeSnapshotProvider}): {@code library}, {@code leaderboard}, {@code cabinets},
 *       {@code events}, {@code wiki}.</li>
 *   <li><b>Actions</b> ({@link ArcadeActionHandler}): {@code export_cartridge}, {@code import_cartridge}. Rate-limited,
 *       idempotent by {@code requestId}, each switchable ({@code link.actions.<name>}).</li>
 *   <li><b>Events</b> ({@link ArcadeEvents}): topic {@code neroarcade.events} — {@code RECORD_BEATEN},
 *       {@code MATCH_RESULT}, {@code CARTRIDGE_DOWNLOADED}; notification text only from {@link ArcadePushCopy}.</li>
 * </ul>
 *
 * <p>Scope is always the requesting player: no section lists players or carries a coordinate or another player's
 * id. The module persists nothing of its own (alerts are Core's; the per-listing download count is the Exchange's);
 * its transient per-player state joins the composite eraser as store {@code "link"}, registered before anything is
 * handed to the registry.
 *
 * <p>{@link #init()} is the last step of common init, guarded: a missing or older Core link API (or anything else)
 * logs one warning and leaves the mod working.
 */
public final class NeroArcadeLinkModule {

    public static final String MODULE_ID = NeroArcadeCommon.MOD_ID;
    public static final int SCHEMA_VERSION = 1;

    public static final String SECTION_LIBRARY = "library";
    public static final String SECTION_LEADERBOARD = "leaderboard";
    public static final String SECTION_CABINETS = "cabinets";
    public static final String SECTION_EVENTS = "events";
    public static final String SECTION_WIKI = "wiki";
    public static final List<String> SECTIONS = List.of(SECTION_LIBRARY, SECTION_LEADERBOARD, SECTION_CABINETS,
            SECTION_EVENTS, SECTION_WIKI);

    public static final String ACTION_EXPORT = "export_cartridge";
    public static final String ACTION_IMPORT = "import_cartridge";
    /** The whole list: nothing else is ever an action ({@code challenge} is cut, D1). */
    public static final List<String> ACTIONS = List.of(ACTION_EXPORT, ACTION_IMPORT);

    /** Window summaries and the digest check run this often (server ticks). */
    static final int TICK_INTERVAL = 20;

    private static final LinkRequestDedup DEDUP = new LinkRequestDedup();
    private static final LinkRateLimiter LIMITER =
            LinkRateLimiter.standard(() -> NeroArcadeConfig.LINK_IMPORT_PER_HOUR.get());
    private static volatile ArcadeEvents.Pipeline pipeline;
    private static volatile boolean registered;
    /** The {@code "link"} eraser this process added (tests remove it again). */
    static volatile NeroArcadeData.StoreEraser activeEraser;

    private NeroArcadeLinkModule() {
    }

    /** Last step of common init. Guarded; it can never break startup. */
    public static void init() {
        init(new Registrar() {
            @Override
            public void register(ArcadeSnapshotProvider snapshots, ArcadeActionHandler actions, LinkModuleInfo info) {
                NeroLinkRegistry.registerSnapshotProvider(snapshots, info);
                NeroLinkRegistry.registerActionHandler(actions, info);
            }
        }, NeroArcadeConfig.LINK_ENABLED.get());
    }

    /** What hands the module to Core (a seam so a test can make it throw). */
    interface Registrar {
        void register(ArcadeSnapshotProvider snapshots, ArcadeActionHandler actions, LinkModuleInfo info);
    }

    /** @return true when the module was registered */
    static synchronized boolean init(Registrar registrar, boolean enabled) {
        NeroArcadeData.StoreEraser linkEraser = null;
        boolean handedOut = false;
        try {
            if (!enabled) {
                NeroArcadeCommon.LOGGER.info("[NeroArcade] link module disabled by config (link.enabled=false).");
                return false;
            }
            if (registered) {
                return true;
            }
            LiveArcadeLinkContext ctx = LiveArcadeLinkContext.INSTANCE;
            ArcadeEvents.Pipeline events = new ArcadeEvents.Pipeline(ctx);
            // Erasure first, so no transient per-player state can exist without its eraser.
            linkEraser = eraser(events);
            NeroArcadeData.addStore(linkEraser);
            activeEraser = linkEraser;
            LinkModuleInfo info = ArcadeLinkInfo.info();
            handedOut = true;
            pipeline = events;
            registrar.register(new ArcadeSnapshotProvider(ctx, events, LIMITER),
                    new ArcadeActionHandler(ctx, DEDUP, LIMITER), info);
            ServerHooks.onTick(server -> {
                ctx.attach(server);
                if (server.getTickCount() % TICK_INTERVAL == 0) {
                    events.tick();
                }
            });
            ServerHooks.onStopping(server -> {
                ctx.attach(null);
                events.reset();
                DEDUP.clear();
                LIMITER.clear();
            });
            ArcadeMatchEnds.addHook((level, end) -> {
                ctx.attach(level.getServer());
                events.onMatchEnd(LiveArcadeLinkContext.matchEnd(level, end));
            });
            registered = true;
            NeroArcadeCommon.LOGGER.info("[NeroArcade] link module registered ({} sections, {} actions).",
                    SECTIONS.size(), ACTIONS.size());
            return true;
        } catch (RuntimeException | LinkageError e) {
            // A link module is optional; it must never take mod construction down with it. When nothing was handed
            // to Core its eraser goes too; once a provider may be live, the eraser stays with it.
            if (linkEraser != null && !handedOut) {
                NeroArcadeData.removeStore(linkEraser);
                activeEraser = null;
            }
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] link module not registered ({}); the arcade is unaffected.",
                    e.getClass().getName());
            return false;
        }
    }

    /** The {@code "link"} eraser: transient per-player state only, nothing persisted to back up. */
    static NeroArcadeData.StoreEraser eraser(ArcadeEvents.Pipeline events) {
        return new NeroArcadeData.StoreEraser() {
            @Override
            public String name() {
                return "link";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                if (player == null) {
                    return;
                }
                events.forget(player);
                DEDUP.forget(player);
                LIMITER.forget(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                // Nothing persisted: alerts are Core's (erased and backed up by Core).
            }
        };
    }

    /** Whether the link module holds any transient state for {@code player} (erasure-conformance probe). */
    public static boolean holdsAnyFor(UUID player) {
        ArcadeEvents.Pipeline p = pipeline;
        return (p != null && p.holdsAnyFor(player)) || DEDUP.holdsAnyFor(player) || LIMITER.holdsAnyFor(player);
    }

    /** Whether {@link #init()} registered the module in this process. */
    public static boolean isRegistered() {
        return registered;
    }
}
