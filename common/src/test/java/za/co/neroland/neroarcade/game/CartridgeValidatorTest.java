package za.co.neroland.neroarcade.game;

import static za.co.neroland.neroarcade.game.GameDefCodecTest.assertCode;
import static za.co.neroland.neroarcade.game.GameDefCodecTest.load;
import static za.co.neroland.neroarcade.game.GameDefCodecTest.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.ValidationError.Code;
import za.co.neroland.neroarcade.module.TestModules;

/** One case per validator check, in order; the first failure wins. */
class CartridgeValidatorTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    private static JsonObject base() {
        return load("orb_dodge.json");
    }

    @Test
    void check01TooLarge() {
        assertEquals(Code.TOO_LARGE, org.junit.jupiter.api.Assertions.assertThrows(ValidationError.class,
                () -> CartridgeValidator.decodeJson(base().toString(), 100)).code);
    }

    @Test
    void check02NewerSchemaEvenWhenTheRestIsGarbage() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 99);
        root.addProperty("nonsense", true);
        assertCode(Code.NEWER_SCHEMA, root.toString());
    }

    @Test
    void check03Malformed() {
        JsonObject missing = base();
        missing.remove("id");
        assertCode(Code.MALFORMED, missing.toString());
        JsonObject wrongType = base();
        wrongType.addProperty("revision", "three");
        assertCode(Code.MALFORMED, wrongType.toString());
        JsonObject fractional = base();
        slot(fractional, "pawn").get(0).getAsJsonObject().getAsJsonObject("props").addProperty("speed", 1.5);
        assertCode(Code.MALFORMED, fractional.toString());
        assertCode(Code.MALFORMED, "[1, 2]");
        assertCode(Code.MALFORMED, "{not json");
        JsonObject noSchema = base();
        noSchema.remove("schema");
        assertCode(Code.MALFORMED, noSchema.toString());
    }

    @Test
    void check04Title() {
        for (String bad : new String[] {"", "   ", "a\u0007b", "x".repeat(33), "bad​zero"}) {
            JsonObject root = base();
            root.addProperty("title", bad);
            assertCode(Code.BAD_TITLE, root.toString());
        }
    }

    @Test
    void check05Id() {
        for (String bad : new String[] {"ABCDEFGHIJKLMNOP", "abc", "abcdefghijklmno1", "abcdefghijklmnopq"}) {
            JsonObject root = base();
            root.addProperty("id", bad);
            assertCode(Code.BAD_ID, root.toString());
        }
    }

    @Test
    void check06Lineage() {
        JsonObject tooLong = base();
        JsonArray nine = new JsonArray();
        for (int i = 0; i < 9; i++) {
            nine.add("bbbbbbbbbbbbbbb" + (char) ('a' + i));
        }
        tooLong.add("lineage", nine);
        assertCode(Code.BAD_LINEAGE, tooLong.toString());
        JsonObject self = base();
        JsonArray own = new JsonArray();
        own.add(self.get("id").getAsString());
        self.add("lineage", own);
        assertCode(Code.BAD_LINEAGE, self.toString());
        JsonObject invalid = base();
        JsonArray bad = new JsonArray();
        bad.add("NOPE");
        invalid.add("lineage", bad);
        assertCode(Code.BAD_LINEAGE, invalid.toString());
    }

    @Test
    void check07Credit() {
        for (String bad : new String[] {"", "x".repeat(17), "0f6c1d2e-3a4b", "tab\there"}) {
            JsonObject root = base();
            root.addProperty("author_credit", bad);
            assertCode(Code.BAD_CREDIT, root.toString());
        }
    }

    @Test
    void check08UnknownSlot() {
        JsonObject root = base();
        root.getAsJsonObject("slots").add("bogus", new JsonArray());
        assertCode(Code.UNKNOWN_SLOT, root.toString());
    }

    @Test
    void check09StackOverflow() {
        JsonObject root = base();
        JsonArray hazards = slot(root, "hazard");
        hazards.add(hazards.get(0).deepCopy());
        hazards.add(hazards.get(0).deepCopy());
        assertCode(Code.STACK_OVERFLOW, root.toString());
    }

    @Test
    void check10UnknownModule() {
        JsonObject root = base();
        slot(root, "hazard").get(1).getAsJsonObject().addProperty("id", "othermod:hazard/lava");
        assertCode(Code.UNKNOWN_MODULE, root.toString());
    }

    @Test
    void check11WrongSlot() {
        JsonObject root = base();
        slot(root, "arena").get(0).getAsJsonObject().addProperty("id", "neroarcade:pawn/cursor");
        assertCode(Code.WRONG_SLOT, root.toString());
    }

    @Test
    void check12BadProperty() {
        JsonObject root = base();
        slot(root, "rule").get(0).getAsJsonObject().getAsJsonObject("props").addProperty("target", 0);
        assertCode(Code.BAD_PROPERTY, root.toString());
    }

    @Test
    void check13OverBudget() {
        JsonObject root = base();
        JsonObject arena = slot(root, "arena").get(0).getAsJsonObject();
        arena.addProperty("id", "neroarcade:arena/test_sized");
        JsonObject props = new JsonObject();
        props.addProperty("side", 40);
        arena.add("props", props);
        assertCode(Code.OVER_BUDGET, root.toString());
    }

    @Test
    void firstFailureWins() {
        JsonObject root = base();
        root.addProperty("title", "");
        root.addProperty("id", "BAD");
        slot(root, "pawn").get(0).getAsJsonObject().addProperty("id", "neroarcade:pawn/missing");
        assertCode(Code.BAD_TITLE, root.toString());
        root.addProperty("title", "Fine");
        assertCode(Code.BAD_ID, root.toString());
        root.addProperty("id", "aaaaaaaaaaaaaaaa");
        assertCode(Code.UNKNOWN_MODULE, root.toString());
    }

    @Test
    void unknownPropertyIsIgnoredAndFlagged() throws ValidationError {
        JsonObject root = base();
        slot(root, "pawn").get(0).getAsJsonObject().getAsJsonObject("props").addProperty("wobble", 4);
        GameDef def = GameDefCodecTest.json(root.toString());
        assertEquals(4, ((za.co.neroland.neroarcade.module.PropertyValue.IntValue) def.slot(
                za.co.neroland.neroarcade.module.ModuleType.PAWN).get(0).props().get("wobble")).value());
        assertTrue(CartridgeValidator.validate(def).unknownProperties());
        assertTrue(Map.of().isEmpty());
        TestFiles.repoRoot();
    }
}
