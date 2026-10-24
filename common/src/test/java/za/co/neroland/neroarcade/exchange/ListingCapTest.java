package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * 003 §8.1 / §12 item 6: the listing cap refuses the 201st listing and evicts nothing; unpublishing frees a slot;
 * re-publishing the same id replaces its row (keeping listingId, plays, hearts, publishedAt) even at the cap; listing
 * ids are never reused.
 */
class ListingCapTest {

    private static final int CAP = 200;

    @Test
    void the201stListingIsRefusedAndNothingIsEvicted() {
        ExchangeTable table = new ExchangeTable();
        for (int i = 0; i < CAP; i++) {
            assertEquals(ExchangeTable.PublishStatus.PUBLISHED,
                    ExchangeFixtures.publish(table, ExchangeFixtures.game(i, ExchangeFixtures.ALICE, null), i, CAP)
                            .status());
        }
        ExchangeTable.PublishResult full = ExchangeFixtures.publish(table,
                ExchangeFixtures.game(CAP, ExchangeFixtures.ALICE, null), 999, CAP);
        assertEquals(ExchangeTable.PublishStatus.FULL, full.status());
        assertTrue(full.listing().isEmpty());
        assertEquals(CAP, table.size());
        assertTrue(table.listingByCartridgeId(ExchangeFixtures.id(0)).isPresent(), "no eviction: the oldest stays");

        // republishing an existing id still works at the cap (a replace, not a new listing)
        assertEquals(ExchangeTable.PublishStatus.REPLACED,
                ExchangeFixtures.publish(table, ExchangeFixtures.game(5, ExchangeFixtures.ALICE, null), 1000, CAP)
                        .status());

        // unpublish frees a slot
        int first = table.listingByCartridgeId(ExchangeFixtures.id(0)).orElseThrow().listingId();
        assertTrue(table.unpublish(first).isPresent());
        assertEquals(ExchangeTable.PublishStatus.PUBLISHED,
                ExchangeFixtures.publish(table, ExchangeFixtures.game(CAP, ExchangeFixtures.ALICE, null), 1001, CAP)
                        .status());
    }

    @Test
    void sameIdPublishReplacesTheRowAndKeepsItsCounters() {
        ExchangeTable table = new ExchangeTable();
        GameDef v1 = ExchangeFixtures.game(ExchangeFixtures.id(7), java.util.List.of(), ExchangeFixtures.ALICE, null, 1);
        Listing first = ExchangeFixtures.publish(table, v1, 50, CAP).listing().orElseThrow();
        table.countPlay(v1.id());
        table.countPlay(v1.id());
        table.heart(first.listingId());

        GameDef v2 = ExchangeFixtures.game(ExchangeFixtures.id(7), java.util.List.of(), ExchangeFixtures.ALICE, null, 2);
        ExchangeTable.PublishResult again = ExchangeFixtures.publish(table, v2, 900, CAP);
        assertEquals(ExchangeTable.PublishStatus.REPLACED, again.status());
        Listing replaced = again.listing().orElseThrow();
        assertEquals(1, table.size());
        assertEquals(first.listingId(), replaced.listingId());
        assertEquals(2, replaced.plays());
        assertEquals(1, replaced.hearts());
        assertEquals(50, replaced.publishedAt(), "a re-publish cannot bump a game up the list");
        assertEquals(2, replaced.gameDef().revision(), "the new revision is what downloads get");
    }

    @Test
    void listingIdsAreNeverReused() {
        ExchangeTable table = new ExchangeTable();
        Listing a = ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, null), 1, CAP)
                .listing().orElseThrow();
        table.unpublish(a.listingId());
        Listing b = ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, null), 2, CAP)
                .listing().orElseThrow();
        assertTrue(b.listingId() > a.listingId());
        ExchangeTable loaded = ExchangeTable.load(table.save());
        assertTrue(loaded.nextListingId() > b.listingId(), "the counter survives a save");
    }
}
