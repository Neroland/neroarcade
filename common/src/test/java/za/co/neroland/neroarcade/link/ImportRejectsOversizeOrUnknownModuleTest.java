package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

/**
 * 007 §8.4: an import is measured on the canonical re-serialisation (12 KiB + 1 → {@code VALIDATION}), runs the same
 * {@code CartridgeValidator} as every in-game path (an unknown module id → {@code VALIDATION}), and a refusal leaves
 * the drafts untouched.
 */
class ImportRejectsOversizeOrUnknownModuleTest {

    @Test
    void oneByteOverTheCapIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject def = LinkFixtures.validGameDef();
        def.remove("author"); // the handler strips it; measure what it measures
        def.addProperty("pad", "");
        int base = def.toString().getBytes(StandardCharsets.UTF_8).length;
        def.addProperty("pad", "p".repeat(ArcadeActionHandler.MAX_GAME_DEF_BYTES + 1 - base));
        assertEquals(ArcadeActionHandler.MAX_GAME_DEF_BYTES + 1, def.toString().getBytes(StandardCharsets.UTF_8).length);

        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", def));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error(), LinkFixtures.describe(r));
        assertEquals("cartridge refused: too_large", r.message());
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty());

        def.addProperty("pad", "p".repeat(ArcadeActionHandler.MAX_GAME_DEF_BYTES - base));
        LinkActionResult atCap = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r2", def));
        assertTrue(atCap.ok(), "exactly 12 KiB is allowed: " + LinkFixtures.describe(atCap));
    }

    @Test
    void unknownModuleIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject def = LinkFixtures.validGameDef();
        JsonArray arena = def.getAsJsonObject("slots").getAsJsonArray("arena");
        arena.get(0).getAsJsonObject().add("id", new JsonPrimitive("neroarcade:arena/not_a_chip"));
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", def));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
        assertEquals("cartridge refused: unknown_module", r.message());
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty());
    }

    @Test
    void notACartridgeIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject params = new JsonObject();
        params.addProperty("requestId", "r1");
        params.addProperty("gameDef", "rm -rf /");
        assertEquals(LinkActionResult.Error.VALIDATION,
                LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge", params).error());
        JsonObject empty = new JsonObject();
        assertEquals(LinkActionResult.Error.VALIDATION, LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE,
                "import_cartridge", LinkFixtures.importParams("r2", empty)).error());
    }
}
