package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.network.ExchangePagePayload;

/**
 * 003 §8.2 / §11: browse pages of 8 in hearts-then-newest order, the page clamped server side, rows relative to the
 * viewer, and a payload that carries metadata only (no UUID, no game) and survives its codec.
 */
class ExchangePagesTest {

    @BeforeEach
    void modules() {
        TestModules.installShipped();
    }

    @AfterEach
    void reset() {
        TestModules.reset();
    }

    @Test
    void pagesOfEightSortedAndClamped() {
        ExchangeTable table = new ExchangeTable();
        for (int i = 0; i < 20; i++) {
            ExchangeFixtures.publish(table, ExchangeFixtures.game(i, ExchangeFixtures.ALICE, null), i, 200);
        }
        int hearted = table.listingByCartridgeId(ExchangeFixtures.id(3)).orElseThrow().listingId();
        table.heart(hearted);
        List<Listing> browse = table.browse();
        assertEquals(hearted, browse.get(0).listingId(), "hearts first");
        assertEquals(ExchangeFixtures.id(19), browse.get(1).cartridgeId(), "then the newest");

        ExchangePagePayload last = ExchangePages.listings(browse, 99, 8, ExchangeFixtures.BOB, (u, id) -> false, true,
                false, ExchangePages.Status.NONE, 0, 8);
        assertEquals(2, last.page(), "clamped to the last page");
        assertEquals(3, last.pageCount());
        assertEquals(4, last.rows().size());
        ExchangePagePayload first = ExchangePages.listings(browse, -5, 8, ExchangeFixtures.BOB, (u, id) -> id == hearted,
                true, false, ExchangePages.Status.NONE, 0, 8);
        assertEquals(0, first.page());
        assertEquals(8, first.rows().size());
        assertTrue(first.rows().get(0).has(ExchangePagePayload.ROW_HEARTED));
        assertFalse(first.rows().get(0).has(ExchangePagePayload.ROW_MINE), "Bob may not unpublish Alice's game");
        ExchangePagePayload mine = ExchangePages.listings(browse, 0, 8, ExchangeFixtures.ALICE, (u, id) -> false,
                true, false, ExchangePages.Status.NONE, 0, 8);
        assertTrue(mine.rows().get(0).has(ExchangePagePayload.ROW_MINE));
    }

    @Test
    void payloadCarriesNoUuidAndSurvivesItsCodec() {
        ExchangeTable table = new ExchangeTable();
        ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, "Alice"), 1, 200);
        ExchangePagePayload page = ExchangePages.listings(table.browse(), 0, 8, ExchangeFixtures.BOB, (u, id) -> false,
                true, true, ExchangePages.Status.ok("neroarcade.exchange.ok.hearted", "Game"), 2, 8);
        ByteBuf buf = Unpooled.buffer();
        ExchangePagePayload.STREAM_CODEC.encode(buf, page);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        String wire = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(wire.contains(ExchangeFixtures.ALICE.toString()));
        ExchangePagePayload back = ExchangePagePayload.STREAM_CODEC.decode(buf);
        assertEquals(page.rows().size(), back.rows().size());
        ExchangePagePayload.Row row = back.rows().get(0);
        assertEquals("Alice", row.credit());
        assertEquals(ExchangeFixtures.TAGS, row.tags());
        assertEquals(ExchangePages.EMPTY_SLOT, row.slotColours()[0], "an empty slot is grey");
        assertTrue(back.enabled() && back.moderator());
        assertEquals("neroarcade.exchange.ok.hearted", back.statusKey());
        assertEquals(List.of("Game"), back.statusArgs());
        assertEquals(2, back.draftsUsed());
    }

    @Test
    void draftsPageListsOnlyTheOwnersDrafts() {
        ExchangeTable table = new ExchangeTable();
        table.addDraft(ExchangeFixtures.ALICE, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, null), 0, 8);
        table.addDraft(ExchangeFixtures.BOB, ExchangeFixtures.game(2, ExchangeFixtures.BOB, null), 0, 8);
        ExchangePagePayload page = ExchangePages.drafts(table.drafts(ExchangeFixtures.ALICE), 0, 8, 3 * 86_400L, true,
                false, ExchangePages.Status.NONE, 8);
        assertEquals(ExchangePagePayload.TAB_DRAFTS, page.tab());
        assertEquals(1, page.rows().size());
        assertEquals(3, page.rows().get(0).ageDays());
        assertEquals(List.of("grid", "move", "survive", "solo", "neon"), page.rows().get(0).tags(),
                "an all-default game's words come from the default chips");
    }
}
