package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

/**
 * 007 §3.2 / §8.4: the ninth draft is refused ({@code drafts full}); three imports a minute and
 * {@code link.importPerHour} (10) an hour, refused calls not counted; a title the chat filter blocks is refused, and
 * one it blocks late is removed again.
 */
class ImportCartridgeRejectsTest {

    @Test
    void ninthDraftIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        for (int i = 0; i < 8; i++) {
            ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game(LinkFixtures.id(100 + i), "D" + i,
                    LinkFixtures.ALICE, null), 1, 8);
        }
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
        assertEquals("drafts full", r.message());
        assertEquals(8, ctx.exchange.drafts(LinkFixtures.ALICE).size());
    }

    @Test
    void threeAMinuteThenRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeActionHandler handler = LinkFixtures.handler(ctx);
        for (int i = 0; i < 3; i++) {
            assertTrue(handler.execute(LinkFixtures.ALICE, "import_cartridge",
                    LinkFixtures.importParams("m" + i, LinkFixtures.validGameDef())).ok());
        }
        LinkActionResult fourth = handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("m3", LinkFixtures.validGameDef()));
        assertEquals(LinkActionResult.Error.VALIDATION, fourth.error());
        assertTrue(fourth.message().startsWith("rate limited, retry in "), fourth.message());
        assertTrue(handler.execute(LinkFixtures.BOB, "import_cartridge",
                LinkFixtures.importParams("m0", LinkFixtures.validGameDef())).ok(), "limits are per player");
    }

    @Test
    void eleventhInAnHourIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        // Alice's own draft of the fixture: each import keeps the id and replaces it, so only the hourly cap bites
        ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game("k7v2m6q4x5b2z3n6", "Orb Dodge",
                LinkFixtures.ALICE, null), 1, 8);
        ArcadeActionHandler handler = LinkFixtures.handler(ctx);
        for (int i = 0; i < 10; i++) {
            LinkActionResult r = handler.execute(LinkFixtures.ALICE, "import_cartridge",
                    LinkFixtures.importParams("h" + i, LinkFixtures.validGameDef()));
            assertTrue(r.ok(), i + ": " + LinkFixtures.describe(r));
            ctx.now += 21_000L;
        }
        LinkActionResult eleventh = handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("h10", LinkFixtures.validGameDef()));
        assertEquals(LinkActionResult.Error.VALIDATION, eleventh.error());
        assertTrue(eleventh.message().startsWith("rate limited"));
        ctx.now += 60L * 60_000L;
        assertTrue(handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("h11", LinkFixtures.validGameDef())).ok(), "an hour later it is allowed");
    }

    @Test
    void blockedTitleIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.blockedTitles.add("Orb Dodge");
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
        assertEquals("title not allowed", r.message());
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty());
    }

    @Test
    void aSlowFilterNeverAnswersOkThenTakesTheDraftAway() {
        // Stage 7 security review L3: while the chat filter has not decided, nothing is added and the phone is told
        // to retry; the pending answer is not remembered, so the same request id asks again
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.pendingFilter = new CompletableFuture<>();
        ArcadeActionHandler handler = LinkFixtures.handler(ctx);
        LinkActionResult r = handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
        assertEquals(ArcadeActionHandler.TITLE_PENDING, r.message());
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty(), "no draft while the title is undecided");
        ctx.pendingFilter.complete(false);
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty(), "a late verdict changes nothing");

        ctx.pendingFilter = null;
        LinkActionResult retry = handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", LinkFixtures.validGameDef()));
        assertTrue(retry.ok(), "the retry is decided afresh: " + LinkFixtures.describe(retry));
        assertEquals(1, ctx.exchange.drafts(LinkFixtures.ALICE).size());
    }

    @Test
    void controlCharactersInTheTitleAreRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject def = LinkFixtures.validGameDef();
        def.addProperty("title", "Bad‮title");
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("r1", def));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
    }
}
