package za.co.neroland.neroarcade.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;

class GameDefCodecTest {

    private static final List<String> FIELD_ORDER = List.of("schema", "id", "title", "author", "author_credit", "lineage",
            "seed", "slots", "created_at", "revision");

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    static GameDef json(String text) throws ValidationError {
        return CartridgeValidator.decodeJson(text, Budgets.MAX_IMPORT_JSON_BYTES);
    }

    @Test
    void everyExampleCartridgeRoundTripsThroughJsonAndNbt() throws ValidationError {
        List<Path> files = TestFiles.cartridges();
        assertTrue(files.size() >= 3, "need at least three example cartridges");
        for (Path file : files) {
            GameDef fromJson = json(TestFiles.read(file));
            CompoundTag nbt = CartridgeValidator.encode(fromJson);
            assertEquals(fromJson, CartridgeValidator.decode(nbt), file.toString());
            String exported = GameDefCodecs.toJson(fromJson);
            assertEquals(fromJson, json(exported), file.toString());
            assertEquals(fromJson, CartridgeValidator.decode(CartridgeValidator.encode(json(exported))), file.toString());
            CartridgeValidator.validate(fromJson);
        }
    }

    @Test
    void fieldsAreWrittenInTheContractOrderAndSlotsInSlotOrder() throws ValidationError {
        for (Path file : TestFiles.cartridges()) {
            JsonObject out = JsonParser.parseString(GameDefCodecs.toJson(json(TestFiles.read(file)))).getAsJsonObject();
            List<String> keys = new ArrayList<>(out.keySet());
            List<String> expected = FIELD_ORDER.stream().filter(keys::contains).toList();
            assertEquals(expected, keys, file.toString());
            List<String> slots = new ArrayList<>(out.getAsJsonObject("slots").keySet());
            List<String> slotOrder = java.util.Arrays.stream(ModuleType.values()).map(ModuleType::key)
                    .filter(slots::contains).toList();
            assertEquals(slotOrder, slots, file.toString());
        }
    }

    @Test
    void absentOptionalsStayAbsent() throws ValidationError {
        GameDef def = json(TestFiles.cartridge("default_survive.json"));
        assertTrue(def.authorUuid().isEmpty());
        assertTrue(def.authorCredit().isEmpty());
        assertTrue(def.seed().isEmpty());
        JsonObject out = JsonParser.parseString(GameDefCodecs.toJson(def)).getAsJsonObject();
        assertFalse(out.has("author"));
        assertFalse(out.has("author_credit"));
        assertFalse(out.has("seed"));
        GameDef orb = json(TestFiles.cartridge("orb_dodge.json"));
        assertEquals(42L, orb.seed().orElseThrow());
        assertEquals(TestModules.AUTHOR, orb.authorUuid().orElseThrow());
    }

    @Test
    void propertiesEqualToTheirDefaultAreDroppedOnRead() throws ValidationError {
        JsonObject root = load("orb_dodge.json");
        slot(root, "pawn").get(0).getAsJsonObject().getAsJsonObject("props").addProperty("speed", 3);
        GameDef def = json(root.toString());
        assertTrue(def.slot(ModuleType.PAWN).get(0).props().isEmpty());
    }

    @Test
    void unknownModuleIsRejected() {
        JsonObject root = load("orb_dodge.json");
        slot(root, "pawn").get(0).getAsJsonObject().addProperty("id", "neroarcade:pawn/not_a_chip");
        assertCode(ValidationError.Code.UNKNOWN_MODULE, root.toString());
    }

    @Test
    void newerSchemaIsRejected() {
        JsonObject root = load("orb_dodge.json");
        root.addProperty("schema", ArcadeSchema.CURRENT + 1);
        assertCode(ValidationError.Code.NEWER_SCHEMA, root.toString());
    }

    @Test
    void sizeCapIsEnforced() throws ValidationError {
        String text = TestFiles.cartridge("orb_dodge.json");
        ValidationError json = assertThrows(ValidationError.class,
                () -> CartridgeValidator.decodeJson(text, text.length() - 1));
        assertEquals(ValidationError.Code.TOO_LARGE, json.code);
        CompoundTag nbt = CartridgeValidator.encode(json(text));
        net.minecraft.nbt.ListTag lineage = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 1000; i++) {
            lineage.add(net.minecraft.nbt.StringTag.valueOf("aaaaaaaaaaaaaaab"));
        }
        nbt.put("lineage", lineage);
        assertEquals(ValidationError.Code.TOO_LARGE,
                assertThrows(ValidationError.class, () -> CartridgeValidator.decode(nbt)).code);
    }

    @Test
    void stackOverflowIsRejected() {
        JsonObject root = load("coop_mirror.json");
        JsonArray hazards = slot(root, "hazard");
        hazards.add(hazards.get(0).deepCopy());
        assertCode(ValidationError.Code.STACK_OVERFLOW, root.toString());
        JsonObject single = load("orb_dodge.json");
        slot(single, "pawn").add(slot(single, "pawn").get(0).deepCopy());
        assertCode(ValidationError.Code.STACK_OVERFLOW, single.toString());
    }

    @Test
    void badPropertyIsRejected() {
        JsonObject root = load("orb_dodge.json");
        slot(root, "pawn").get(0).getAsJsonObject().getAsJsonObject("props").addProperty("speed", 9);
        assertCode(ValidationError.Code.BAD_PROPERTY, root.toString());
        JsonObject constant = load("orb_dodge.json");
        slot(constant, "arena").get(0).getAsJsonObject().getAsJsonObject("props").addProperty("obstacles", "lava");
        assertCode(ValidationError.Code.BAD_PROPERTY, constant.toString());
    }

    static JsonObject load(String name) {
        return JsonParser.parseString(TestFiles.cartridge(name)).getAsJsonObject();
    }

    static JsonArray slot(JsonObject root, String key) {
        return root.getAsJsonObject("slots").getAsJsonArray(key);
    }

    static void assertCode(ValidationError.Code code, String json) {
        ValidationError error = assertThrows(ValidationError.class, () -> json(json));
        assertEquals(code, error.code, error.getMessage());
        assertEquals("neroarcade.cartridge.error." + code.name().toLowerCase(java.util.Locale.ROOT), error.langKey);
    }
}
