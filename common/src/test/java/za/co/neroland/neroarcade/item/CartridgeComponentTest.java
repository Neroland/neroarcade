package za.co.neroland.neroarcade.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * The {@code neroarcade:game_def} item component: a GameDef survives the component's persistent codec exactly, and
 * a cartridge this server cannot read survives untouched (lossless, fail closed).
 */
class CartridgeComponentTest {

    @BeforeEach
    void registry() {
        TestModules.installShipped();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static GameDef sample() {
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.RULE, List.of(new SlotEntry(ModuleDefaults.RULE, Map.of("lives", new PropertyValue.IntValue(3)))));
        slots.put(ModuleType.THEME, List.of(SlotEntry.of(ModuleDefaults.THEME)));
        return new GameDef(1, TestModules.ID, "Neon Dodge", Optional.of(TestModules.AUTHOR), Optional.empty(), List.of(),
                Optional.of(42L), slots, 1760976000L, 3);
    }

    /** The component type exactly as {@code ModDataComponents} builds it. */
    private static DataComponentType<StoredCartridge> componentType() {
        return DataComponentType.<StoredCartridge>builder().persistent(StoredCartridge.CODEC).build();
    }

    @Test
    void gameDefRoundTripsThroughTheComponentCodec() throws ValidationError {
        GameDef def = sample();
        StoredCartridge stored = StoredCartridge.of(def);
        Tag written = componentType().codecOrThrow().encodeStart(NbtOps.INSTANCE, stored).getOrThrow();
        StoredCartridge read = componentType().codecOrThrow().parse(NbtOps.INSTANCE, written).getOrThrow();
        assertEquals(def, read.decode());
        assertEquals(stored, read);
        assertEquals("Neon Dodge", read.rawTitle());
        assertEquals(TestModules.ID, read.rawId());
        assertTrue(read.error().isEmpty());
    }

    @Test
    void newerCartridgeSurvivesUntouchedAndReadsAsUnreadable() throws ValidationError {
        CompoundTag tag = StoredCartridge.of(sample()).tag();
        tag.putInt("schema", 99);
        tag.put("slots", tag.getCompoundOrEmpty("slots").copy());
        tag.getCompoundOrEmpty("slots").put("future_slot", new net.minecraft.nbt.ListTag());
        StoredCartridge future = new StoredCartridge(tag);
        Tag written = StoredCartridge.CODEC.encodeStart(NbtOps.INSTANCE, future).getOrThrow();
        StoredCartridge read = StoredCartridge.CODEC.parse(NbtOps.INSTANCE, written).getOrThrow();
        assertEquals(tag, read.tag(), "lossless: nothing is dropped from a cartridge this server cannot read");
        assertEquals(ValidationError.Code.NEWER_SCHEMA, read.error().orElseThrow().code);
        assertTrue(read.tryDecode().isEmpty());
    }

    @Test
    void oversizeCartridgeIsRefusedNotTruncated() {
        GameDef def = sample();
        GameDef huge = new GameDef(1, def.id(), def.title(), def.authorUuid(), Optional.of("x".repeat(9000)),
                def.lineage(), def.seed(), def.slots(), def.createdAt(), def.revision());
        ValidationError error = org.junit.jupiter.api.Assertions.assertThrows(ValidationError.class,
                () -> StoredCartridge.of(huge));
        assertEquals(ValidationError.Code.TOO_LARGE, error.code);
    }

    @Test
    void bandTintIsTheThemeSignatureColour() {
        assertEquals(0xFF3FA4, CartridgeTint.of(sample()));
        assertEquals(0xFF3FA4, CartridgeTint.of(TestModules.emptyDef()), "an empty THEME slot is the default theme");
        assertEquals(0x7FD7FF, CartridgeTint.ofTheme(TestModules.id("theme/hangar")));
        assertEquals(CartridgeTint.GREY, CartridgeTint.ofTheme(TestModules.id("theme/unknown")));
    }
}
