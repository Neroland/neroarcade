package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 003 §8.2: a player's drafts bucket holds at most 8 (the 9th is refused), a draft of the same id replaces the old
 * one, fetching frees a slot, and drafts older than 30 days go in the sweep. Buckets are per owner.
 */
class DraftsCapTest {

    private static final int CAP = 8;
    private static final long DAY = 86_400L;

    @Test
    void theNinthDraftIsRefused() {
        ExchangeTable table = new ExchangeTable();
        for (int i = 0; i < CAP; i++) {
            assertEquals(ExchangeTable.DraftStatus.ADDED, table.addDraft(ExchangeFixtures.ALICE,
                    ExchangeFixtures.game(i, ExchangeFixtures.ALICE, null), 10, CAP));
        }
        assertEquals(ExchangeTable.DraftStatus.FULL, table.addDraft(ExchangeFixtures.ALICE,
                ExchangeFixtures.game(99, ExchangeFixtures.ALICE, null), 10, CAP));
        assertEquals(CAP, table.drafts(ExchangeFixtures.ALICE).size());
        assertEquals(ExchangeTable.DraftStatus.REPLACED, table.addDraft(ExchangeFixtures.ALICE,
                ExchangeFixtures.game(3, ExchangeFixtures.ALICE, null), 20, CAP), "same id replaces, even when full");
        assertEquals(ExchangeTable.DraftStatus.ADDED, table.addDraft(ExchangeFixtures.BOB,
                ExchangeFixtures.game(99, ExchangeFixtures.BOB, null), 10, CAP), "buckets are per player");

        assertTrue(table.takeDraft(ExchangeFixtures.ALICE, 0).isPresent(), "fetch");
        assertEquals(ExchangeTable.DraftStatus.ADDED, table.addDraft(ExchangeFixtures.ALICE,
                ExchangeFixtures.game(99, ExchangeFixtures.ALICE, null), 30, CAP));
        assertTrue(table.takeDraft(ExchangeFixtures.ALICE, 42).isEmpty(), "a bad index takes nothing");
    }

    @Test
    void draftsOlderThanThirtyDaysArePurged() {
        ExchangeTable table = new ExchangeTable();
        long now = 100 * DAY;
        table.addDraft(ExchangeFixtures.ALICE, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, null), now - 31 * DAY, CAP);
        table.addDraft(ExchangeFixtures.ALICE, ExchangeFixtures.game(2, ExchangeFixtures.ALICE, null), now - 29 * DAY, CAP);
        table.addDraft(ExchangeFixtures.BOB, ExchangeFixtures.game(3, ExchangeFixtures.BOB, null), now - 40 * DAY, CAP);
        assertEquals(2, table.purgeDrafts(now, ExchangeStore.DRAFT_RETENTION_SECONDS));
        assertEquals(1, table.drafts(ExchangeFixtures.ALICE).size());
        assertEquals(ExchangeFixtures.id(2), table.drafts(ExchangeFixtures.ALICE).get(0).cartridgeId());
        assertTrue(table.drafts(ExchangeFixtures.BOB).isEmpty());
        assertTrue(!table.holdsDrafts(ExchangeFixtures.BOB), "an emptied bucket is gone");
        ExchangeTable loaded = ExchangeTable.load(table.save());
        assertEquals(1, loaded.drafts(ExchangeFixtures.ALICE).size(), "drafts survive a save");
    }
}
