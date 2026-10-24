package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * {@code export_cartridge} by cartridge id (D-owner-1: boards and listings are per id). A published remix shares
 * its original's lineage root; exporting it by its id returns the remix, never the original. The id is required
 * (Stage 7 security review M2): there is no by-root lookup, because lineage is author-controlled and "the newest
 * listing of a root" could be hijacked. Scope rules are unchanged: any listing is public, the author tombstoned
 * unless it is the requester.
 */
class ExportByIdTest {

    private static final String ORIGINAL = LinkFixtures.id(41);
    private static final String REMIX = LinkFixtures.id(42);

    private static GameDef remixOf(String original, UUID author) {
        return new GameDef(1, REMIX, "Easy Dodge", Optional.of(author), Optional.empty(), List.of(original),
                Optional.empty(), Map.of(), 100L, 1);
    }

    private static FakeLinkContext twoListings() {
        FakeLinkContext ctx = new FakeLinkContext();
        LinkFixtures.publish(ctx, LinkFixtures.game(ORIGINAL, "Neon Dodge", LinkFixtures.BOB, null), 1);
        // the remix is newer and shares the original's root: only the id tells them apart
        LinkFixtures.publish(ctx, remixOf(ORIGINAL, LinkFixtures.KESSLER), 2);
        return ctx;
    }

    @Test
    void exportingAPublishedRemixByItsIdReturnsTheRemix() {
        FakeLinkContext ctx = twoListings();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", ORIGINAL, REMIX));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(REMIX, r.state().get("id").getAsString());
        assertEquals(ORIGINAL, r.state().get("lineageRoot").getAsString(), "informational");
        assertEquals("Easy Dodge", r.state().get("title").getAsString());
        JsonObject def = r.state().getAsJsonObject("gameDef");
        assertEquals(Tombstones.ERASED_AUTHOR.toString(), def.get("authorUuid").getAsString());
    }

    @Test
    void exportingTheOriginalByItsIdReturnsTheOriginalNotTheNewerRemix() {
        FakeLinkContext ctx = twoListings();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", ORIGINAL, ORIGINAL));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(ORIGINAL, r.state().get("id").getAsString());
        assertEquals("Neon Dodge", r.state().get("title").getAsString());
    }

    @Test
    void anIdThatIsNeitherListedNorYoursIsNotOwnedEvenWhenItsRootIsListed() {
        FakeLinkContext ctx = twoListings();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", ORIGINAL, LinkFixtures.id(43)));
        assertEquals(LinkActionResult.Error.NOT_OWNER, r.error(), "no silent fallback to the original");
    }

    @Test
    void anIdAloneIsEnough() {
        FakeLinkContext ctx = twoListings();
        JsonObject params = new JsonObject();
        params.addProperty("requestId", "r1");
        params.addProperty("id", REMIX);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge", params);
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(REMIX, r.state().get("id").getAsString());
    }

    @Test
    void withoutAnIdIsAValidationError() {
        FakeLinkContext ctx = twoListings();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", ORIGINAL, null));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error(), "a lineage root alone answers nothing");
        assertEquals("bad cartridge", r.message());
    }

    @Test
    void aForgedLineageRootIsIgnored() {
        // lineageRoot is informational: whatever it says, the id decides
        FakeLinkContext ctx = twoListings();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", LinkFixtures.id(77), ORIGINAL));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(ORIGINAL, r.state().get("id").getAsString());
    }

    @Test
    void neitherIdNorRootIsAValidationError() {
        LinkActionResult r = LinkFixtures.handler(new FakeLinkContext()).execute(LinkFixtures.ALICE,
                "export_cartridge", new JsonObject());
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
    }
}
