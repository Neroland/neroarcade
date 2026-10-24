package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

/**
 * 007 §8.5: the same {@code requestId} twice gives one draft and the same result object, without spending a second
 * rate-limit slot; an id over 64 characters is {@code VALIDATION}; ids expire after ten minutes and are per player
 * and per action; erasure forgets them.
 */
class LinkRequestDedupTest {

    @Test
    void replayReturnsTheFirstResultWithoutReapplying() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeActionHandler handler = LinkFixtures.handler(ctx);
        JsonObject params = LinkFixtures.importParams("same-id", LinkFixtures.validGameDef());
        LinkActionResult first = handler.execute(LinkFixtures.ALICE, "import_cartridge", params);
        assertTrue(first.ok());
        ctx.exchange.takeDraft(LinkFixtures.ALICE, 0); // fetched at a kiosk in the meantime
        for (int i = 0; i < 5; i++) {
            assertSame(first, handler.execute(LinkFixtures.ALICE, "import_cartridge", params));
        }
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty(), "the replay applied nothing");
        // replays spent no rate-limit slots: two more distinct imports still fit in the minute
        assertTrue(handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("b", LinkFixtures.validGameDef())).ok());
        assertTrue(handler.execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("c", LinkFixtures.validGameDef())).ok());
    }

    @Test
    void overlongIdIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("x".repeat(65), LinkFixtures.validGameDef()));
        assertEquals(LinkActionResult.Error.VALIDATION, r.error());
        assertTrue(ctx.exchange.drafts(LinkFixtures.ALICE).isEmpty());
    }

    @Test
    void scopedExpiringAndErasable() {
        LinkRequestDedup dedup = new LinkRequestDedup();
        LinkActionResult result = LinkActionResult.ok(new JsonObject());
        dedup.remember(LinkFixtures.ALICE, "import_cartridge", "id1", result, 0L);
        assertSame(result, dedup.lookup(LinkFixtures.ALICE, "import_cartridge", "id1", 1L));
        assertNull(dedup.lookup(LinkFixtures.BOB, "import_cartridge", "id1", 1L), "per player");
        assertNull(dedup.lookup(LinkFixtures.ALICE, "export_cartridge", "id1", 1L), "per action");
        assertNull(dedup.lookup(LinkFixtures.ALICE, "import_cartridge", "id1", LinkRequestDedup.TTL_MS), "expired");
        dedup.remember(LinkFixtures.ALICE, "import_cartridge", "id2", result, 0L);
        assertTrue(dedup.holdsAnyFor(LinkFixtures.ALICE));
        dedup.forget(LinkFixtures.ALICE);
        assertFalse(dedup.holdsAnyFor(LinkFixtures.ALICE));
        dedup.remember(LinkFixtures.ALICE, "import_cartridge", null, result, 0L);
        assertFalse(dedup.holdsAnyFor(LinkFixtures.ALICE), "no id, nothing kept");
    }
}
