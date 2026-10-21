package za.co.neroland.neroarcade.game;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;

/**
 * The cartridge codecs. {@link #GAME_DEF} is the strict codec (slot keys must be {@link ModuleType}s) used
 * for the item data component and for encoding; {@link #RAW} has string slot keys so the validator can
 * report an unknown slot as {@code unknown_slot} rather than {@code malformed}.
 *
 * <p><b>Invariant:</b> both write the fields in the same order — {@code schema, id, title, author,
 * author_credit, lineage, seed, slots, created_at, revision} — and slots in {@link ModuleType} order; the
 * encoder never trusts map iteration order.
 */
public final class GameDefCodecs {

    /** A UUID as its canonical string. */
    public static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(UUID.fromString(text));
        } catch (IllegalArgumentException bad) {
            return DataResult.error(() -> "not a UUID: " + text);
        }
    }, UUID::toString);

    public static final Codec<Map<ModuleType, List<SlotEntry>>> SLOTS =
            Codec.unboundedMap(ModuleType.CODEC, SlotEntry.CODEC.listOf());

    public static final Codec<GameDef> GAME_DEF = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schema").forGetter(GameDef::schemaVersion),
            Codec.STRING.fieldOf("id").forGetter(GameDef::id),
            Codec.STRING.fieldOf("title").forGetter(GameDef::title),
            UUID_STRING.optionalFieldOf("author").forGetter(GameDef::authorUuid),
            Codec.STRING.optionalFieldOf("author_credit").forGetter(GameDef::authorCredit),
            Codec.STRING.listOf().fieldOf("lineage").forGetter(GameDef::lineage),
            Codec.LONG.optionalFieldOf("seed").forGetter(GameDef::seed),
            SLOTS.fieldOf("slots").forGetter(GameDef::slots),
            Codec.LONG.fieldOf("created_at").forGetter(GameDef::createdAt),
            Codec.INT.fieldOf("revision").forGetter(GameDef::revision)
    ).apply(i, GameDef::new));

    /** A cartridge as decoded before semantic checks: slot keys are still strings. */
    record RawGameDef(int schema, String id, String title, Optional<UUID> author, Optional<String> credit,
            List<String> lineage, Optional<Long> seed, Map<String, List<SlotEntry>> slots, long createdAt,
            int revision) {
    }

    static final Codec<RawGameDef> RAW = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schema").forGetter(RawGameDef::schema),
            Codec.STRING.fieldOf("id").forGetter(RawGameDef::id),
            Codec.STRING.fieldOf("title").forGetter(RawGameDef::title),
            UUID_STRING.optionalFieldOf("author").forGetter(RawGameDef::author),
            Codec.STRING.optionalFieldOf("author_credit").forGetter(RawGameDef::credit),
            Codec.STRING.listOf().fieldOf("lineage").forGetter(RawGameDef::lineage),
            Codec.LONG.optionalFieldOf("seed").forGetter(RawGameDef::seed),
            Codec.unboundedMap(Codec.STRING, SlotEntry.CODEC.listOf()).fieldOf("slots").forGetter(RawGameDef::slots),
            Codec.LONG.fieldOf("created_at").forGetter(RawGameDef::createdAt),
            Codec.INT.fieldOf("revision").forGetter(RawGameDef::revision)
    ).apply(i, RawGameDef::new));

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GameDefCodecs() {
    }

    /** The {@code .nac} export form: pretty JSON. */
    public static String toJson(GameDef def) {
        return PRETTY.toJson(GAME_DEF.encodeStart(JsonOps.INSTANCE, def).getOrThrow());
    }
}
