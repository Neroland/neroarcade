package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.board.BoardTestAccess;
import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.network.BoardPagePayload;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 007 §3.2 and the Stage 7 security review (H1): the importer is always the author (any author or credit in the
 * payload is ignored), credit is cleared, {@code createdAt} is now, the draft lands only in the importer's own
 * bucket, and the payload's id is kept <b>only</b> when the importer authored it on this server (a draft of theirs or
 * their own listing). Any other id — someone else's listing, someone else's unpublished cartridge, a {@code .nac}
 * from another server — is re-minted and the import becomes a remix on the same lineage, so it can never share, or
 * post onto, that id's board.
 */
class ImportAuthorIsRequesterTest {

    /** The id inside {@code orb_dodge.json}. */
    private static final String FIXTURE_ID = "k7v2m6q4x5b2z3n6";

    @Test
    void payloadAuthorAndCreditAreIgnored() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject def = LinkFixtures.validGameDef();
        def.addProperty("author", LinkFixtures.BOB.toString());
        def.addProperty("authorUuid", LinkFixtures.BOB.toString());
        def.addProperty("author_credit", "BobTheBuilder");
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", def));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(1, ctx.exchange.drafts(LinkFixtures.ALICE).size());
        assertTrue(ctx.exchange.drafts(LinkFixtures.BOB).isEmpty(), "never anyone else's bucket");
        GameDef stored = ctx.exchange.drafts(LinkFixtures.ALICE).get(0).gameDef();
        assertEquals(Optional.of(LinkFixtures.ALICE), stored.authorUuid());
        assertEquals(Optional.empty(), stored.authorCredit());
        assertEquals(ctx.now / 1000L, stored.createdAt());
        assertEquals(3, stored.revision(), "revision kept");
        assertNotEquals(FIXTURE_ID, r.state().get("draftId").getAsString(), "an id Alice never authored here");
        assertEquals(1, r.state().get("draftsUsed").getAsInt());
        assertEquals(8, r.state().get("draftsMax").getAsInt());
    }

    @Test
    void someoneElsesListedIdIsReminted() {
        FakeLinkContext ctx = new FakeLinkContext();
        LinkFixtures.publish(ctx, LinkFixtures.game(FIXTURE_ID, "Orb Dodge", LinkFixtures.BOB, null), 1);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        Draft draft = ctx.exchange.drafts(LinkFixtures.ALICE).get(0);
        assertNotEquals(FIXTURE_ID, draft.cartridgeId());
        assertEquals(FIXTURE_ID, draft.gameDef().lineageRoot(), "the remix keeps the original's lineage root (its board is its own new id)");
        assertEquals(FIXTURE_ID, r.state().get("lineageRoot").getAsString());
    }

    @Test
    void foreignUnlistedIdIsReminted() {
        FakeLinkContext ctx = new FakeLinkContext();
        // Bob's unpublished cartridge: a draft in his bucket, no listing anywhere
        ctx.exchange.addDraft(LinkFixtures.BOB, LinkFixtures.game(FIXTURE_ID, "Bob's secret", LinkFixtures.BOB, null),
                1, 8);
        assertTrue(ctx.exchange.listingByCartridgeId(FIXTURE_ID).isEmpty());
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        String draftId = r.state().get("draftId").getAsString();
        assertNotEquals(FIXTURE_ID, draftId);
        GameDef stored = ctx.exchange.draft(LinkFixtures.ALICE, draftId).orElseThrow().gameDef();
        List<String> lineage = stored.lineage();
        assertEquals(FIXTURE_ID, lineage.get(lineage.size() - 1), "a remix of the id it named: " + lineage);
        assertEquals(FIXTURE_ID, ctx.exchange.drafts(LinkFixtures.BOB).get(0).cartridgeId(), "Bob's draft untouched");
    }

    @Test
    void anIdUnknownOnThisServerIsReminted() {
        // a .nac from another server: nobody here authored it, so it is a remix with its own board
        FakeLinkContext ctx = new FakeLinkContext();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertNotEquals(FIXTURE_ID, r.state().get("draftId").getAsString());
        assertEquals(FIXTURE_ID, r.state().get("lineageRoot").getAsString());
    }

    @Test
    void foreignIdWithABoardNeverSharesIt() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.boards.put(FIXTURE_ID, LinkFixtures.board(FIXTURE_ID, LinkFixtures.BOB, 500, "BobName"));
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        Draft draft = ctx.exchange.drafts(LinkFixtures.ALICE).get(0);
        assertNotEquals(FIXTURE_ID, draft.cartridgeId(), "the draft's id is not the board key");
        assertNotEquals(BoardKeys.cartridgeKey(FIXTURE_ID, FIXTURE_ID),
                BoardKeys.cartridgeKey(draft.cartridgeId(), draft.gameDef().lineageRoot()));
        assertFalse(ctx.boards.containsKey(draft.cartridgeId()), "a fresh board of its own");
    }

    @Test
    void anUnlistedIdSeenOnABoardPageCannotPostOntoThatBoard() {
        // Bob's private cartridge has a board; every client near the cabinet receives its id in a BoardPagePayload
        LeaderboardTable boards = new LeaderboardTable();
        BoardTestAccess.record(boards, match(FIXTURE_ID, FIXTURE_ID, LinkFixtures.BOB, 500), 1L);
        BoardPagePayload page = new BoardPagePayload(FIXTURE_ID, List.of(new BoardPagePayload.Row(1, "", 500)), -1);
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.exchange.addDraft(LinkFixtures.BOB, LinkFixtures.game(FIXTURE_ID, "Bob's secret", LinkFixtures.BOB, null),
                1, 8);

        // Alice forges a trivial cartridge carrying that id and imports it from her phone
        JsonObject forged = LinkFixtures.validGameDef();
        forged.addProperty("id", page.cartridgeId());
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", forged));
        assertTrue(r.ok(), LinkFixtures.describe(r));

        // the kiosk FETCH writes the draft's game verbatim onto a Blank (Exchange.fetchDraft → CartridgeItem.create)
        GameDef onTheItem = ctx.exchange.takeDraft(LinkFixtures.ALICE, 0).orElseThrow().gameDef();
        assertNotEquals(page.cartridgeId(), onTheItem.id());

        // she plays it at her own cabinet: the result is keyed by the item's id
        BoardTestAccess.record(boards, match(onTheItem.lineageRoot(), onTheItem.id(), LinkFixtures.ALICE, 99_999), 2L);
        LeaderboardTable.Board bobs = boards.board(FIXTURE_ID).orElseThrow();
        assertEquals(1, bobs.top().size(), "Bob's board is unchanged");
        assertEquals(LinkFixtures.BOB, bobs.top().get(0).uuid());
        assertFalse(bobs.personalBest().containsKey(LinkFixtures.ALICE));
        assertEquals(99_999, boards.board(onTheItem.id()).orElseThrow().top().get(0).score(), "her own board");
    }

    @Test
    void ownListedIdIsKept() {
        FakeLinkContext ctx = new FakeLinkContext();
        LinkFixtures.publish(ctx, LinkFixtures.game(FIXTURE_ID, "Orb Dodge", LinkFixtures.ALICE, null), 1);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertEquals(FIXTURE_ID, r.state().get("draftId").getAsString());
    }

    @Test
    void ownDraftIdIsKept() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game(FIXTURE_ID, "Orb Dodge", LinkFixtures.ALICE, null),
                1, 8);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(FIXTURE_ID, r.state().get("draftId").getAsString(), "an honest round trip of your own draft");
    }

    @Test
    void missingIdIsMinted() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject def = LinkFixtures.validGameDef();
        def.remove("id");
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", def));
        assertTrue(r.ok(), LinkFixtures.describe(r));
        assertEquals(16, r.state().get("draftId").getAsString().length());
        assertEquals(r.state().get("draftId").getAsString(), r.state().get("lineageRoot").getAsString(),
                "a freshly minted id is not treated as a remix of itself");
    }

    private static BusMatchResult match(String root, String cartridgeId, java.util.UUID player, int score) {
        return new BusMatchResult(root, cartridgeId, List.of(new BusMatchResult.SeatResult(player, 0, score, 1, false)),
                400, 7L, MatchResult.Outcome.WIN, BusMatchResult.EndedBy.RULE, 1L);
    }
}
