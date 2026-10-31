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
        return PRETTY.toJson(contractOrder(GAME_DEF.encodeStart(JsonOps.INSTANCE, def).getOrThrow()));
    }

    /** The top-level fields of an exported cartridge, in the documented contract order. */
    private static final List<String> FIELD_ORDER = List.of("schema", "id", "title", "author", "author_credit",
            "lineage", "seed", "slots", "created_at", "revision");

    /**
     * Puts an encoded cartridge's fields in the contract order (and its slots in slot order, each entry as id then
     * props), so an export reads the same on every Minecraft line. 26.x's codecs already write that order; the
     * older DataFixerUpper on the 1.21.x lines writes record fields in a different one.
     */
    public static com.google.gson.JsonElement contractOrder(com.google.gson.JsonElement encoded) {
        if (!(encoded instanceof com.google.gson.JsonObject in)) {
            return encoded;
        }
        com.google.gson.JsonObject out = new com.google.gson.JsonObject();
        for (String key : FIELD_ORDER) {
            if (!in.has(key)) {
                continue;
            }
            if (key.equals("slots") && in.get(key) instanceof com.google.gson.JsonObject slots) {
                com.google.gson.JsonObject ordered = new com.google.gson.JsonObject();
                for (ModuleType type : ModuleType.values()) {
                    if (slots.has(type.key())) {
                        ordered.add(type.key(), entriesInOrder(slots.get(type.key())));
                    }
                }
                slots.entrySet().forEach(e -> {
                    if (!ordered.has(e.getKey())) {
                        ordered.add(e.getKey(), e.getValue());
                    }
                });
                out.add(key, ordered);
            } else {
                out.add(key, in.get(key));
            }
        }
        in.entrySet().forEach(e -> {
            if (!out.has(e.getKey())) {
                out.add(e.getKey(), e.getValue());
            }
        });
        return out;
    }

    private static com.google.gson.JsonElement entriesInOrder(com.google.gson.JsonElement list) {
        if (!(list instanceof com.google.gson.JsonArray array)) {
            return list;
        }
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement element : array) {
            if (element instanceof com.google.gson.JsonObject entry) {
                com.google.gson.JsonObject ordered = new com.google.gson.JsonObject();
                for (String key : List.of("id", "props")) {
                    if (entry.has(key)) {
                        ordered.add(key, entry.get(key));
                    }
                }
                entry.entrySet().forEach(e -> {
                    if (!ordered.has(e.getKey())) {
                        ordered.add(e.getKey(), e.getValue());
                    }
                });
                out.add(ordered);
            } else {
                out.add(element);
            }
        }
        return out;
    }
}
