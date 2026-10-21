package za.co.neroland.neroarcade.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValue.BoolValue;
import za.co.neroland.neroarcade.module.PropertyValue.EnumValue;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;

/** The maximal legal cartridge fits both caps with room to spare; an oversized one is refused, never cut. */
class CartridgeSizeTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    private static SlotEntry entry(String path, Object... kv) {
        Map<String, PropertyValue> props = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            props.put((String) kv[i], v instanceof Integer n ? new IntValue(n)
                    : v instanceof Boolean b ? new BoolValue(b) : new EnumValue((String) v));
        }
        return new SlotEntry(TestModules.id(path), props);
    }

    static GameDef maximal() {
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.ARENA, List.of(entry("arena/grid_16", "obstacles", "maze_lite")));
        slots.put(ModuleType.PAWN, List.of(entry("pawn/cursor", "speed", 5)));
        slots.put(ModuleType.RULE, List.of(entry("rule/test_score", "target", 9999, "lives", 9)));
        List<SlotEntry> hazards = new ArrayList<>();
        List<SlotEntry> pickups = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            hazards.add(entry("hazard/test_bouncer", "count", 4, "speed", 5));
            pickups.add(entry("pickup/test_orb", "value", 100, "count", 8));
        }
        slots.put(ModuleType.HAZARD, hazards);
        slots.put(ModuleType.PICKUP, pickups);
        slots.put(ModuleType.CONTROL, List.of(entry("control/four_way", "diagonals", true)));
        slots.put(ModuleType.PACE, List.of(entry("pace/steady", "divisor", 4)));
        slots.put(ModuleType.MODIFIER, List.of(entry("modifier/test_mirror")));
        slots.put(ModuleType.MULTIPLAYER, List.of(entry("multiplayer/test_coop", "seats", 4, "shared_lives", false)));
        slots.put(ModuleType.THEME, List.of(entry("theme/neon_void", "variant", 3)));
        List<String> lineage = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            lineage.add("bbbbbbbbbbbbbbb" + (char) ('a' + i));
        }
        return new GameDef(1, "abcdefghijklmnop", "W".repeat(32), Optional.of(TestModules.AUTHOR),
                Optional.of("C".repeat(16)), lineage, Optional.of(Long.MIN_VALUE), slots, Long.MAX_VALUE,
                Integer.MAX_VALUE);
    }

    @Test
    void maximalCartridgeFitsBothCaps() throws ValidationError {
        GameDef def = maximal();
        CartridgeValidator.validate(def);
        CompoundTag nbt = CartridgeValidator.encode(def);
        int nbtBytes = CartridgeValidator.nbtSize(nbt);
        int jsonBytes = GameDefCodecs.toJson(def).getBytes(StandardCharsets.UTF_8).length;
        assertTrue(nbtBytes <= Budgets.MAX_CARTRIDGE_BYTES, "maximal cartridge NBT bytes = " + nbtBytes);
        assertTrue(jsonBytes <= Budgets.MAX_IMPORT_JSON_BYTES, "maximal cartridge JSON bytes = " + jsonBytes);
        assertTrue(nbtBytes * 3 <= Budgets.MAX_CARTRIDGE_BYTES, "expected generous headroom; NBT = " + nbtBytes);
        assertEquals(def, CartridgeValidator.decode(nbt));
    }

    @Test
    void paddedCartridgeIsTooLargeNotTruncated() throws ValidationError {
        CompoundTag nbt = CartridgeValidator.encode(maximal());
        ListTag lineage = new ListTag();
        for (int i = 0; i < 600; i++) {
            lineage.add(StringTag.valueOf("cccccccccccccccc"));
        }
        nbt.put("lineage", lineage);
        ValidationError error = assertThrows(ValidationError.class, () -> CartridgeValidator.decode(nbt));
        assertEquals(ValidationError.Code.TOO_LARGE, error.code);
        assertEquals("8.0", error.args.get(1));
    }
}
