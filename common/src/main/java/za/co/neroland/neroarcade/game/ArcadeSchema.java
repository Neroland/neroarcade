package za.co.neroland.neroarcade.game;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;

/**
 * The cartridge schema version and its migrations.
 *
 * <p><b>Invariants:</b> the {@code schema} field is read <em>before</em> the record codec runs, so a future
 * shape change can never break the version check itself; a cartridge from a newer schema fails closed
 * ({@code newer_schema}); an older one is migrated step by step to {@link #CURRENT} before decoding. Schema 1
 * is the {@link GameDef} record; 0.1.0 ships no migration steps.
 */
public final class ArcadeSchema {

    public static final int CURRENT = 1;

    private ArcadeSchema() {
    }

    /** Migrates {@code raw} from schema {@code from} to {@link #CURRENT} and decodes it strictly. */
    public static DataResult<GameDef> migrate(int from, Dynamic<?> raw) {
        return decode(from, raw);
    }

    private static <T> DataResult<GameDef> decode(int from, Dynamic<T> raw) {
        return upgrade(from, raw).flatMap(GameDef.CODEC::parse);
    }

    /** Reads the {@code schema} int alone; an absent or non-integer field is an error. */
    public static DataResult<Integer> readSchema(Dynamic<?> raw) {
        return raw.get("schema").asNumber().flatMap(number -> {
            int value = number.intValue();
            return number.longValue() == value && number.toString().indexOf('.') < 0
                    ? DataResult.success(value)
                    : DataResult.error(() -> "schema is not an integer: " + number);
        });
    }

    /** Applies the migration steps {@code from..CURRENT-1}; errors for a newer or nonsensical version. */
    static <T> DataResult<Dynamic<T>> upgrade(int from, Dynamic<T> raw) {
        if (from > CURRENT) {
            return DataResult.error(() -> "newer_schema: " + from + " > " + CURRENT);
        }
        if (from < 1) {
            return DataResult.error(() -> "bad schema version " + from);
        }
        Dynamic<T> current = raw;
        for (int version = from; version < CURRENT; version++) {
            current = step(version, current);
        }
        return DataResult.success(current);
    }

    /** One migration step from {@code version} to {@code version + 1}. Add a case per historical version. */
    private static <T> Dynamic<T> step(int version, Dynamic<T> raw) {
        // 0.1.0 ships schema 1 only; the first change adds: if (version == 1) { return v1ToV2(raw); }
        throw new IllegalStateException("no migration from schema " + version + " (" + raw.getValue() + ")");
    }
}
