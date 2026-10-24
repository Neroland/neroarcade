package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * 007 §3.1 / D3: any published listing may be exported by anyone (the author tombstoned unless the requester is the
 * author; credit only as the listing shows it); a draft only by its owner (someone else's draft id answers
 * {@code NOT_OWNER}, or the public listing when that id is listed); an own draft carries the requester's id.
 */
class ExportScopeTest {

    @Test
    void publishedByAnotherIsPublicWithTheAuthorTombstoned() {
        FakeLinkContext ctx = new FakeLinkContext();
        String root = LinkFixtures.id(1);
        // Bob's game carries his credit, but he had credit off when it was published: the listing shows none
        ctx.exchange.publish(LinkFixtures.game(root, "Neon Dodge", LinkFixtures.BOB, "BobTheBuilder"), LinkFixtures.BOB,
                java.util.Optional.empty(), LinkFixtures.TAGS, 1, 10);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", root, root));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        JsonObject def = r.state().getAsJsonObject("gameDef");
        assertEquals(Tombstones.ERASED_AUTHOR.toString(), def.get("authorUuid").getAsString());
        assertFalse(def.has("author"), "the stored author field never leaves");
        assertFalse(r.state().toString().contains(LinkFixtures.BOB.toString()));
        assertFalse(def.has("author_credit"), "Bob's credit was not shown on the listing (credit off)");
        assertFalse(r.state().toString().contains("BobTheBuilder"));
        assertEquals(root, r.state().get("id").getAsString());
        assertEquals("Neon Dodge", def.get("title").getAsString());
        assertTrue(r.state().get("bytes").getAsInt() > 0);
    }

    @Test
    void creditOnlyAsTheListingShowsIt() {
        FakeLinkContext ctx = new FakeLinkContext();
        String root = LinkFixtures.id(2);
        ctx.exchange.publish(LinkFixtures.game(root, "Neon Dodge", LinkFixtures.BOB, "StarPilot"), LinkFixtures.BOB,
                java.util.Optional.of("StarPilot"), LinkFixtures.TAGS, 1, 10);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", root, root));
        assertEquals("StarPilot", r.state().getAsJsonObject("gameDef").get("author_credit").getAsString());
    }

    @Test
    void someoneElsesDraftIsNotYours() {
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(3);
        ctx.exchange.addDraft(LinkFixtures.BOB, LinkFixtures.game(id, "Secret", LinkFixtures.BOB, null), 1, 8);
        LinkActionResult byId = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", id, id));
        assertFalse(byId.ok());
        assertEquals(LinkActionResult.Error.NOT_OWNER, byId.error());
        LinkActionResult byRoot = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r2", id, null));
        assertEquals(LinkActionResult.Error.VALIDATION, byRoot.error(), "no id, no lookup (review M2)");
    }

    @Test
    void aDraftIdThatIsAlsoListedAnswersTheListing() {
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(4);
        LinkFixtures.publish(ctx, LinkFixtures.game(id, "Public", LinkFixtures.BOB, null), 1);
        ctx.exchange.addDraft(LinkFixtures.BOB, LinkFixtures.game(id, "Bob's private edit", LinkFixtures.BOB, null), 1,
                8);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", id, id));
        assertTrue(r.ok());
        assertEquals("Public", r.state().get("title").getAsString());
    }

    @Test
    void ownDraftCarriesOwnAuthor() {
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(5);
        ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game(id, "Mine", LinkFixtures.ALICE, null), 1, 8);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", id, id));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(LinkFixtures.ALICE.toString(), r.state().getAsJsonObject("gameDef").get("authorUuid").getAsString());
    }

    @Test
    void ownListingCarriesOwnAuthor() {
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(6);
        LinkFixtures.publish(ctx, LinkFixtures.game(id, "Mine", LinkFixtures.ALICE, null), 1);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", id, id));
        assertEquals(LinkFixtures.ALICE.toString(), r.state().getAsJsonObject("gameDef").get("authorUuid").getAsString());
    }

    @Test
    void badIdsAreValidationErrors() {
        FakeLinkContext ctx = new FakeLinkContext();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", "../../etc", "../../etc"));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
    }

    @Test
    void disabledActionAndExchangeSayActionDisabled() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.settings = LinkSettings.DEFAULTS.withActions(false, true, true);
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE,
                "export_cartridge", LinkFixtures.exportParams("r1", LinkFixtures.id(1), LinkFixtures.id(1))).error());
        ctx.settings = LinkSettings.DEFAULTS.withActions(true, true, false);
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE,
                "export_cartridge", LinkFixtures.exportParams("r1", LinkFixtures.id(1), LinkFixtures.id(1))).error());
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE,
                "import_cartridge", LinkFixtures.importParams("r1", new JsonObject())).error());
    }
}
