package za.co.neroland.neroarcade.progression;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * NeroArcade's progression records (003 §9). Two kinds, never mixed:
 *
 * <ul>
 *   <li><b>Threshold channels</b> (world facts, {@code "scope":"server"} quests only): {@link #MATCH_WON},
 *       {@link #CARTRIDGE_PUBLISHED}, {@link #RECORD_SET}. The scope is a <b>place or system</b> — a cartridge's lineage
 *       root, or {@code "exchange"} — <b>never a player</b>; the value is the world's running count and the threshold
 *       the highest rung at or below it.</li>
 *   <li><b>Player gates</b> (personal milestones, offline-safe): {@link #FIRST_CARTRIDGE}, {@link #FIRST_PUBLISHED},
 *       {@link #FIRST_VERSUS_WIN}, defined in {@code data/neroarcade/neroland_gates/}. Opening one twice is a no-op.</li>
 * </ul>
 *
 * Everything goes through a {@link Sink} so the rules run in a plain JVM; the game installs {@link #CORE}. Server
 * thread only. Nothing here logs a player.
 */
public final class ArcadeMilestones {

    public static final Identifier MATCH_WON = id("match_won");
    public static final Identifier CARTRIDGE_PUBLISHED = id("cartridge_published");
    public static final Identifier RECORD_SET = id("record_set");

    public static final Identifier FIRST_CARTRIDGE = id("first_cartridge");
    public static final Identifier FIRST_PUBLISHED = id("first_published");
    public static final Identifier FIRST_VERSUS_WIN = id("first_versus_win");

    /** The Exchange's scope string. */
    public static final String EXCHANGE_SCOPE = "exchange";

    static final long[] WIN_RUNGS = {1L, 10L, 100L};
    static final long[] PUBLISHED_RUNGS = {1L, 10L, 50L};
    static final long[] RECORD_RUNGS = {1L, 10L};

    /** Where crossings and gate openings go. */
    public interface Sink {
        void fire(ThresholdEvents.ThresholdCrossing crossing);

        /** @return true if the gate was newly opened */
        boolean openGate(MinecraftServer server, UUID player, Identifier gate);
    }

    /** The real sink: Core's threshold bus (sourced as {@code neroarcade}) and Core's gate store. */
    public static final Sink CORE = new Sink() {
        @Override
        public void fire(ThresholdEvents.ThresholdCrossing crossing) {
            ThresholdEvents.fire(NeroArcadeCommon.MOD_ID, crossing);
        }

        @Override
        public boolean openGate(MinecraftServer server, UUID player, Identifier gate) {
            return server != null && ProgressionGates.open(server, player, gate);
        }
    };

    private static volatile Sink sink = CORE;

    private ArcadeMilestones() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, path);
    }

    /** Test seam: {@code null} restores {@link #CORE}. */
    public static void setSink(Sink value) {
        sink = value == null ? CORE : value;
    }

    /** Every channel id (the quest-pack test reads it). */
    public static List<Identifier> channels() {
        return List.of(MATCH_WON, CARTRIDGE_PUBLISHED, RECORD_SET);
    }

    /** Every player gate id. */
    public static List<Identifier> gates() {
        return List.of(FIRST_CARTRIDGE, FIRST_PUBLISHED, FIRST_VERSUS_WIN);
    }

    /** The highest rung at or below {@code count} (at least the first rung). */
    static long rungFor(long count, long[] rungs) {
        long rung = rungs[0];
        for (long candidate : rungs) {
            if (candidate <= count) {
                rung = candidate;
            }
        }
        return rung;
    }

    // --- channels ----------------------------------------------------------------------------------

    /** A match on {@code lineageRoot}'s board was won; {@code wins} is that board's win count, this one included. */
    public static boolean matchWon(String lineageRoot, long wins) {
        return fire(MATCH_WON, lineageRoot, wins, WIN_RUNGS);
    }

    /** A new listing went up; {@code listings} is the Exchange's listing count now. */
    public static boolean cartridgePublished(long listings) {
        return fire(CARTRIDGE_PUBLISHED, EXCHANGE_SCOPE, listings, PUBLISHED_RUNGS);
    }

    /** A new top score on {@code lineageRoot}'s board; {@code records} counts such records on it. */
    public static boolean recordSet(String lineageRoot, long records) {
        return fire(RECORD_SET, lineageRoot, records, RECORD_RUNGS);
    }

    private static boolean fire(Identifier channel, String scope, long value, long[] rungs) {
        if (scope == null || scope.isEmpty() || value < 1) {
            return false;
        }
        sink.fire(new ThresholdEvents.ThresholdCrossing(channel, scope, value, rungFor(value, rungs), true));
        return true;
    }

    // --- gates -------------------------------------------------------------------------------------

    public static boolean firstCartridge(MinecraftServer server, UUID player) {
        return open(server, player, FIRST_CARTRIDGE);
    }

    public static boolean firstPublished(MinecraftServer server, UUID player) {
        return open(server, player, FIRST_PUBLISHED);
    }

    public static boolean firstVersusWin(MinecraftServer server, UUID player) {
        return open(server, player, FIRST_VERSUS_WIN);
    }

    private static boolean open(MinecraftServer server, UUID player, Identifier gate) {
        if (player == null || Tombstones.isTombstone(player)) {
            return false;
        }
        try {
            return sink.openGate(server, player, gate);
        } catch (RuntimeException e) {
            NeroArcadeCommon.LOGGER.debug("[NeroArcade] gate {} not opened ({})", gate, e.getClass().getSimpleName());
            return false;
        }
    }
}
