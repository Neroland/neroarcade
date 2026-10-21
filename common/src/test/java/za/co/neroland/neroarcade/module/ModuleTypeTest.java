package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;

class ModuleTypeTest {

    @Test
    void tenSlotsInTheLockedOrder() {
        assertArrayEquals(new ModuleType[] {ModuleType.ARENA, ModuleType.PAWN, ModuleType.RULE, ModuleType.HAZARD,
                ModuleType.PICKUP, ModuleType.CONTROL, ModuleType.PACE, ModuleType.MODIFIER, ModuleType.MULTIPLAYER,
                ModuleType.THEME}, ModuleType.values());
        for (ModuleType type : ModuleType.values()) {
            assertEquals(type.ordinal(), type.slot, type.name());
            boolean stack = type == ModuleType.HAZARD || type == ModuleType.PICKUP;
            assertEquals(stack ? 3 : 1, type.maxStack, type.name());
        }
    }

    @Test
    void coloursMatchTheVisualLanguageTable() {
        int[] expected = {0x2FE3FF, 0xFF3FA4, 0xFFB347, 0xFF5C5C, 0x58E07A, 0xE8ECF4, 0xB48CFF, 0x8F62FF, 0x22B8D8, 0xFF8CCF};
        for (ModuleType type : ModuleType.values()) {
            assertEquals(expected[type.slot], type.colour, type.name());
        }
    }

    @Test
    void codecRoundTripsEveryKeyAndRejectsUnknown() {
        for (ModuleType type : ModuleType.values()) {
            var json = ModuleType.CODEC.encodeStart(JsonOps.INSTANCE, type).getOrThrow();
            assertEquals(new JsonPrimitive(type.key()), json);
            assertEquals(type, ModuleType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        assertTrue(ModuleType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("bogus")).error().isPresent());
        assertTrue(ModuleType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("ARENA")).error().isPresent());
    }

    @Test
    void everySlotHasNameAndDescriptionLang() {
        Map<String, String> lang = TestFiles.lang();
        for (ModuleType type : ModuleType.values()) {
            assertTrue(lang.containsKey(type.langKey()), type.langKey());
            assertTrue(lang.containsKey(type.descLangKey()), type.descLangKey());
        }
    }
}
