package za.co.neroland.neroarcade.game;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.GameDefCodecs.RawGameDef;
import za.co.neroland.neroarcade.game.ValidationError.Code;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.PaceModule;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.ThemeModule;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * The one gate every cartridge passes — at insert, duplicate, publish, download, import and bench save.
 *
 * <p><b>Invariants:</b> checks run in a fixed order and the first failure wins: (1) size, (2) schema,
 * (3) codec, (4) title, (5) id, (6) lineage, (7) credit, (8) slot keys, (9) stack sizes, (10) module ids,
 * (11) module slot, (12) properties, (13) server budgets. Every failure is a refusal ({@link ValidationError});
 * nothing is truncated, clamped or substituted. An unknown module id is {@code unknown_module}, never a silent
 * drop or a default. Decoding returns the <em>canonical</em> cartridge: known properties coerced to their
 * kind and values equal to their default omitted; undeclared properties are kept (and warned about) until
 * the next save drops them.
 */
public final class CartridgeValidator {

    /** A UUID fragment ({@code 8-4} hex groups) or a dashless 32-hex UUID: never allowed as a public credit. */
    private static final Pattern UUID_SHAPED = Pattern.compile("(?i).*([0-9a-f]{8}-[0-9a-f]{4}|[0-9a-f]{32}).*");

    private CartridgeValidator() {
    }

    /** NBT path: size → schema → codec → semantic checks. */
    public static GameDef decode(CompoundTag nbt) throws ValidationError {
        int size = nbtSize(nbt);
        if (size > Budgets.MAX_CARTRIDGE_BYTES) {
            throw tooLarge(size, Budgets.MAX_CARTRIDGE_BYTES);
        }
        RawGameDef raw = decodeRaw(new Dynamic<>(NbtOps.INSTANCE, nbt));
        return semantic(raw).def();
    }

    /**
     * JSON path (NeroLink import, {@code .nac} files): JSON size (before parsing) → parse → schema → codec →
     * the NBT size cap on the result → semantic checks.
     */
    public static GameDef decodeJson(String json, int maxBytes) throws ValidationError {
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxBytes) {
            throw tooLarge(bytes, maxBytes);
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (JsonParseException | IllegalStateException bad) {
            throw new ValidationError(Code.MALFORMED);
        }
        if (!element.isJsonObject()) {
            throw new ValidationError(Code.MALFORMED);
        }
        RawGameDef raw = decodeRaw(new Dynamic<>(JsonOps.INSTANCE, element));
        DataResult<Tag> nbt = GameDefCodecs.RAW.encodeStart(NbtOps.INSTANCE, raw);
        if (nbt.result().isEmpty() || !(nbt.result().get() instanceof CompoundTag tag)) {
            throw new ValidationError(Code.MALFORMED);
        }
        int size = nbtSize(tag);
        if (size > Budgets.MAX_CARTRIDGE_BYTES) {
            throw tooLarge(size, Budgets.MAX_CARTRIDGE_BYTES);
        }
        return semantic(raw).def();
    }

    /** Semantic checks (4–13) on an already-typed cartridge, resolved against the module registry. */
    public static ResolvedGame validate(GameDef def) throws ValidationError {
        checkHeader(def.title(), def.id(), def.lineage(), def.authorCredit());
        return resolve(def);
    }

    /** Encodes for an item or a store and re-checks the size cap. */
    public static CompoundTag encode(GameDef def) throws ValidationError {
        DataResult<Tag> result = GameDef.CODEC.encodeStart(NbtOps.INSTANCE, def);
        if (result.result().isEmpty() || !(result.result().get() instanceof CompoundTag tag)) {
            throw new ValidationError(Code.MALFORMED);
        }
        int size = nbtSize(tag);
        if (size > Budgets.MAX_CARTRIDGE_BYTES) {
            throw tooLarge(size, Budgets.MAX_CARTRIDGE_BYTES);
        }
        return tag;
    }

    /** Uncompressed NBT byte count, the unit of the size cap (the same measurement on every loader). */
    public static int nbtSize(CompoundTag tag) {
        int[] count = {0};
        OutputStream counter = new OutputStream() {
            @Override
            public void write(int b) {
                count[0]++;
            }

            @Override
            public void write(byte[] b, int off, int len) {
                count[0] += len;
            }
        };
        try (DataOutputStream out = new DataOutputStream(counter)) {
            NbtIo.write(tag, out);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return count[0];
    }

    // -----------------------------------------------------------------------------------------------------

    private static <T> RawGameDef decodeRaw(Dynamic<T> dynamic) throws ValidationError {
        Optional<Integer> schema = ArcadeSchema.readSchema(dynamic).result();
        if (schema.isEmpty()) {
            throw new ValidationError(Code.MALFORMED);
        }
        if (schema.get() > ArcadeSchema.CURRENT) {
            throw new ValidationError(Code.NEWER_SCHEMA);
        }
        Optional<RawGameDef> raw = ArcadeSchema.upgrade(schema.get(), dynamic)
                .flatMap(GameDefCodecs.RAW::parse).result();
        if (raw.isEmpty()) {
            throw new ValidationError(Code.MALFORMED);
        }
        return raw.get();
    }

    private record Checked(GameDef def, ResolvedGame game) {
    }

    private static Checked semantic(RawGameDef raw) throws ValidationError {
        checkHeader(raw.title(), raw.id(), raw.lineage(), raw.credit());
        // 8. slot keys
        EnumMap<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        for (Map.Entry<String, List<SlotEntry>> entry : raw.slots().entrySet()) {
            Optional<ModuleType> type = ModuleType.byKey(entry.getKey());
            if (type.isEmpty()) {
                throw new ValidationError(Code.UNKNOWN_SLOT, entry.getKey());
            }
            slots.put(type.get(), entry.getValue());
        }
        GameDef def = new GameDef(raw.schema(), raw.id(), raw.title(), raw.author(), raw.credit(), raw.lineage(),
                raw.seed(), slots, raw.createdAt(), raw.revision());
        ResolvedGame game = resolve(def);
        return new Checked(canonical(def), game);
    }

    /** Checks 4–7. */
    private static void checkHeader(String title, String id, List<String> lineage, Optional<String> credit)
            throws ValidationError {
        String trimmed = title == null ? "" : title.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 1 || length > 32 || hasControl(title)) {
            throw new ValidationError(Code.BAD_TITLE);
        }
        if (!CartridgeIds.isValid(id)) {
            throw new ValidationError(Code.BAD_ID);
        }
        if (lineage.size() > CartridgeIds.MAX_LINEAGE) {
            throw new ValidationError(Code.BAD_LINEAGE);
        }
        for (String ancestor : lineage) {
            if (!CartridgeIds.isValid(ancestor) || ancestor.equals(id)) {
                throw new ValidationError(Code.BAD_LINEAGE);
            }
        }
        if (credit.isPresent()) {
            String text = credit.get();
            int chars = text.codePointCount(0, text.length());
            if (chars < 1 || chars > 16 || hasControl(text) || text.isBlank() || UUID_SHAPED.matcher(text).matches()) {
                // 16 characters cannot hold a whole UUID; refusing a UUID-looking fragment keeps ids out of credits
                throw new ValidationError(Code.BAD_CREDIT);
            }
        }
    }

    private static boolean hasControl(String text) {
        return text.codePoints().anyMatch(c -> {
            int type = Character.getType(c);
            return type == Character.CONTROL || type == Character.FORMAT || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR || type == Character.UNASSIGNED
                    || type == Character.PRIVATE_USE || type == Character.SURROGATE;
        });
    }

    /** Checks 9–13 and builds the resolved game (empty slots → defaults). */
    private static ResolvedGame resolve(GameDef def) throws ValidationError {
        // 9. stack sizes
        for (ModuleType type : ModuleType.values()) {
            if (def.slot(type).size() > type.maxStack) {
                throw new ValidationError(Code.STACK_OVERFLOW, type.key(), Integer.toString(type.maxStack));
            }
        }
        // 10. every id resolves
        for (ModuleType type : ModuleType.values()) {
            for (SlotEntry entry : def.slot(type)) {
                if (ModuleRegistry.get(entry.id()).isEmpty()) {
                    throw new ValidationError(Code.UNKNOWN_MODULE, entry.id().toString());
                }
            }
        }
        // 11. in the right slot
        for (ModuleType type : ModuleType.values()) {
            for (SlotEntry entry : def.slot(type)) {
                if (ModuleRegistry.get(entry.id(), ModuleRegistry.hookOf(type)).isEmpty()
                        || ModuleRegistry.get(entry.id()).orElseThrow().type() != type) {
                    throw new ValidationError(Code.WRONG_SLOT, entry.id().toString(), type.key());
                }
            }
        }
        // 12. properties
        Map<ModuleType, List<PropertyValues>> props = new EnumMap<>(ModuleType.class);
        boolean unknown = false;
        for (ModuleType type : ModuleType.values()) {
            List<PropertyValues> list = new ArrayList<>();
            for (SlotEntry entry : def.slot(type)) {
                ArcadeModule module = ModuleRegistry.get(entry.id()).orElseThrow();
                list.add(PropertyValues.of(module, entry.props()));
                unknown |= PropertyValues.hasUnknown(module, entry.props());
            }
            props.put(type, list);
        }
        ResolvedGame game = new ResolvedGame(def,
                single(def, ModuleType.ARENA, ArenaModule.class), singleProps(def, props, ModuleType.ARENA),
                single(def, ModuleType.PAWN, PawnModule.class), singleProps(def, props, ModuleType.PAWN),
                single(def, ModuleType.RULE, RuleModule.class), singleProps(def, props, ModuleType.RULE),
                stack(def, ModuleType.HAZARD, HazardModule.class), props.get(ModuleType.HAZARD),
                stack(def, ModuleType.PICKUP, PickupModule.class), props.get(ModuleType.PICKUP),
                single(def, ModuleType.CONTROL, ControlModule.class), singleProps(def, props, ModuleType.CONTROL),
                single(def, ModuleType.PACE, PaceModule.class), singleProps(def, props, ModuleType.PACE),
                stack(def, ModuleType.MODIFIER, ModifierModule.class).stream().findFirst(),
                props.get(ModuleType.MODIFIER).stream().findFirst(),
                single(def, ModuleType.MULTIPLAYER, MultiplayerModule.class),
                singleProps(def, props, ModuleType.MULTIPLAYER),
                single(def, ModuleType.THEME, ThemeModule.class), singleProps(def, props, ModuleType.THEME),
                unknown);
        // 13. server budgets
        int side = Budgets.maxArenaSide();
        int w = game.arenaWidth();
        int h = game.arenaHeight();
        if (w > side || h > side || w < Budgets.MIN_ARENA_SIDE || h < Budgets.MIN_ARENA_SIDE) {
            throw new ValidationError(Code.OVER_BUDGET, "arena " + w + "x" + h + ", limit " + side);
        }
        int seats = game.multiplayer().seats(game.multiplayerProps());
        if (seats < 1 || seats > Budgets.MAX_SEATS) {
            throw new ValidationError(Code.OVER_BUDGET, "seats " + seats + ", limit " + Budgets.MAX_SEATS);
        }
        return game;
    }

    private static <M extends ArcadeModule> M single(GameDef def, ModuleType type, Class<M> hook) {
        List<SlotEntry> entries = def.slot(type);
        Identifier id = entries.isEmpty()
                ? ModuleDefaults.defaultFor(type).orElseThrow(() -> new IllegalStateException("no default for " + type))
                : entries.get(0).id();
        return ModuleRegistry.get(id, hook).orElseThrow(() -> new IllegalStateException(
                "default " + type.key() + " module " + id + " is not registered"));
    }

    private static PropertyValues singleProps(GameDef def, Map<ModuleType, List<PropertyValues>> props, ModuleType type) {
        List<PropertyValues> list = props.get(type);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        Identifier id = ModuleDefaults.defaultFor(type).orElseThrow();
        return PropertyValues.defaults(ModuleRegistry.get(id).orElseThrow());
    }

    private static <M extends ArcadeModule> List<M> stack(GameDef def, ModuleType type, Class<M> hook) {
        List<M> out = new ArrayList<>();
        for (SlotEntry entry : def.slot(type)) {
            out.add(ModuleRegistry.get(entry.id(), hook).orElseThrow());
        }
        return out;
    }

    /** Known props coerced to their kind with defaults omitted; undeclared props kept raw. */
    private static GameDef canonical(GameDef def) throws ValidationError {
        EnumMap<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        for (Map.Entry<ModuleType, List<SlotEntry>> slot : def.slots().entrySet()) {
            List<SlotEntry> entries = new ArrayList<>();
            for (SlotEntry entry : slot.getValue()) {
                ArcadeModule module = ModuleRegistry.get(entry.id()).orElseThrow();
                Map<String, PropertyValue> props = new LinkedHashMap<>();
                for (Map.Entry<String, PropertyValue> prop : entry.props().entrySet()) {
                    Optional<PropertySpec> spec = module.properties().stream()
                            .filter(s -> s.name().equals(prop.getKey())).findFirst();
                    if (spec.isEmpty()) {
                        props.put(prop.getKey(), prop.getValue());
                    } else {
                        PropertyValue coerced = spec.get().coerce(prop.getValue());
                        if (!coerced.equals(spec.get().defaultValue())) {
                            props.put(prop.getKey(), coerced);
                        }
                    }
                }
                entries.add(new SlotEntry(entry.id(), props));
            }
            slots.put(slot.getKey(), entries);
        }
        return new GameDef(def.schemaVersion(), def.id(), def.title(), def.authorUuid(), def.authorCredit(),
                def.lineage(), def.seed(), slots, def.createdAt(), def.revision());
    }

    private static ValidationError tooLarge(int bytes, int limit) {
        return new ValidationError(Code.TOO_LARGE, kb(bytes), kb(limit));
    }

    /** Bytes as KiB with one decimal, rounded up, integer arithmetic only. */
    static String kb(int bytes) {
        long tenths = (bytes * 10L + 1023) / 1024;
        return (tenths / 10) + "." + (tenths % 10);
    }
}
