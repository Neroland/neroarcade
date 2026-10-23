package za.co.neroland.neroarcade.runtime;

import java.util.Arrays;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neroarcade.config.NeroArcadeConfig;

/**
 * Every limit the runtime enforces. Capitalised constants are hard limits; the methods read the
 * server-authoritative {@code runtime.*} config keys in {@link NeroArcadeConfig}.
 *
 * <p><b>Invariant:</b> a match reads its configurable budgets once, at {@code ArcadeRuntime.newState} /
 * {@code start}, and never again, so a config reload never changes a running match.
 */
public final class Budgets {

    public static final int MAX_PACE_DIVISOR = 4;
    public static final int MAX_SEATS = 4;
    public static final int MAX_CARTRIDGE_BYTES = 8192;
    public static final int MAX_IMPORT_JSON_BYTES = 12288;
    public static final int MAX_REPLAY_ENTRIES = 20000;
    public static final int RESPAWN_TICKS = 20;
    /**
     * SHIELD ticks granted on every housekeeping respawn (001 A15.b). Bound: a seat conceding a goal at tick
     * {@code t} is shielded until {@code t + 40}; a re-served ball (serve delay 40) first moves at {@code t + 42}.
     */
    public static final int RESPAWN_GRACE_TICKS = 20;
    public static final int COUNTDOWN_TICKS = 60;
    /** Base ticks per second. */
    public static final int TICKS_PER_SECOND = 20;
    /** Smallest arena side the runtime builds. */
    public static final int MIN_ARENA_SIDE = 4;
    /** Hard ceiling of {@code runtime.maxArenaSide} (32 cells: {@code s16} positions on the wire). */
    public static final int HARD_MAX_ARENA_SIDE = 32;
    /** Hard ceiling of {@code runtime.maxEntities}. */
    public static final int HARD_MAX_ENTITIES = 256;
    /**
     * The largest keyframe {@code SnapshotCodec} can produce (32x32 raw cells, 256 entities, four seats):
     * the {@code runtime.snapshotMaxBytes} floor is above it, so a keyframe always fits.
     */
    public static final int WORST_CASE_KEYFRAME_BYTES = 6239;

    /** The configurable budgets, for test overrides. */
    enum Key {
        MAX_ARENA_SIDE, MAX_ENTITIES, MAX_INPUT_PACKETS_PER_SECOND, SNAPSHOT_MAX_BYTES, SNAPSHOT_HZ,
        KEYFRAME_TICKS, MAX_AWAKE_CABINETS, SLEEP_AFTER_TICKS, SCREEN_RENDER_RANGE, MAX_MATCH_SECONDS
    }

    private static final int UNSET = Integer.MIN_VALUE;
    private static final int[] OVERRIDES = new int[Key.values().length];

    static {
        Arrays.fill(OVERRIDES, UNSET);
    }

    private Budgets() {
    }

    public static int maxArenaSide() {
        return read(Key.MAX_ARENA_SIDE, NeroArcadeConfig.MAX_ARENA_SIDE);
    }

    public static int maxEntities() {
        return read(Key.MAX_ENTITIES, NeroArcadeConfig.MAX_ENTITIES);
    }

    public static int maxInputPacketsPerSecond() {
        return read(Key.MAX_INPUT_PACKETS_PER_SECOND, NeroArcadeConfig.MAX_INPUT_PACKETS_PER_SECOND);
    }

    public static int snapshotMaxBytes() {
        return read(Key.SNAPSHOT_MAX_BYTES, NeroArcadeConfig.SNAPSHOT_MAX_BYTES);
    }

    public static int snapshotHz() {
        return read(Key.SNAPSHOT_HZ, NeroArcadeConfig.SNAPSHOT_HZ);
    }

    public static int keyframeTicks() {
        return read(Key.KEYFRAME_TICKS, NeroArcadeConfig.KEYFRAME_TICKS);
    }

    public static int maxAwakeCabinets() {
        return read(Key.MAX_AWAKE_CABINETS, NeroArcadeConfig.MAX_AWAKE_CABINETS);
    }

    public static int sleepAfterTicks() {
        return read(Key.SLEEP_AFTER_TICKS, NeroArcadeConfig.SLEEP_AFTER_TICKS);
    }

    public static int screenRenderRange() {
        return read(Key.SCREEN_RENDER_RANGE, NeroArcadeConfig.SCREEN_RENDER_RANGE);
    }

    public static int maxMatchSeconds() {
        return read(Key.MAX_MATCH_SECONDS, NeroArcadeConfig.MAX_MATCH_SECONDS);
    }

    private static int read(Key key, ConfigValue<Integer> value) {
        synchronized (OVERRIDES) {
            int override = OVERRIDES[key.ordinal()];
            if (override != UNSET) {
                return override;
            }
        }
        return value.get();
    }

    /** Tests only: pins a budget regardless of config. */
    static void overrideForTests(Key key, int value) {
        synchronized (OVERRIDES) {
            OVERRIDES[key.ordinal()] = value;
        }
    }

    /** Tests only: back to config values. */
    static void clearTestOverrides() {
        synchronized (OVERRIDES) {
            Arrays.fill(OVERRIDES, UNSET);
        }
    }
}
