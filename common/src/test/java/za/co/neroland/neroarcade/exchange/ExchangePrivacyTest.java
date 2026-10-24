package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * 003 §6.3–6.4 / §12 item 8: turning credit off strips the name from every listing and draft the player authored;
 * erasure deletes the player's listings and drafts and tombstones any other row naming them as author (a tombstoned
 * author has no credit); both stores are registered for erasure and tolerate a server-less run.
 */
class ExchangePrivacyTest {

    @Test
    void optOutStripsCreditFromListingsAndDrafts() {
        ExchangeTable table = new ExchangeTable();
        ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, "Alice"), 1, 200);
        ExchangeFixtures.publish(table, ExchangeFixtures.game(2, ExchangeFixtures.BOB, "Bob"), 1, 200);
        table.addDraft(ExchangeFixtures.CAROL, ExchangeFixtures.game(3, ExchangeFixtures.ALICE, "Alice"), 1, 8);

        assertTrue(table.stripCredit(ExchangeFixtures.ALICE));
        Listing alice = table.listingByCartridgeId(ExchangeFixtures.id(1)).orElseThrow();
        assertEquals(Optional.empty(), alice.credit());
        assertEquals(Optional.empty(), alice.gameDef().authorCredit(), "downloads no longer carry the name either");
        assertEquals(Optional.of("Bob"), table.listingByCartridgeId(ExchangeFixtures.id(2)).orElseThrow().credit());
        assertEquals(Optional.empty(), table.drafts(ExchangeFixtures.CAROL).get(0).gameDef().authorCredit());
        assertFalse(table.stripCredit(ExchangeFixtures.ALICE), "nothing left to strip");
        assertFalse(ExchangeTable.load(table.save()).save().toString().contains("Alice"));
    }

    @Test
    void erasureRemovesListingsAndDraftsAndTombstonesAuthoredRows() {
        ExchangeTable table = new ExchangeTable();
        ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, "Alice"), 1, 200);
        // an op listed Bob's copy-of-a-game whose author field still names Alice (direct authorship only)
        GameDef aliceGame = ExchangeFixtures.game(2, ExchangeFixtures.ALICE, "Alice");
        table.publish(aliceGame, ExchangeFixtures.BOB, Optional.empty(), ExchangeFixtures.TAGS, 2, 200);
        ExchangeFixtures.publish(table, ExchangeFixtures.game(3, ExchangeFixtures.BOB, null), 3, 200);
        table.addDraft(ExchangeFixtures.ALICE, ExchangeFixtures.game(4, ExchangeFixtures.ALICE, null), 1, 8);
        table.addDraft(ExchangeFixtures.CAROL, ExchangeFixtures.game(5, ExchangeFixtures.ALICE, "Alice"), 1, 8);

        assertTrue(table.holdsAuthor(ExchangeFixtures.ALICE));
        assertTrue(table.holdsDrafts(ExchangeFixtures.ALICE));
        assertTrue(table.eraseListings(ExchangeFixtures.ALICE));
        assertTrue(table.eraseDrafts(ExchangeFixtures.ALICE));

        assertTrue(table.listingByCartridgeId(ExchangeFixtures.id(1)).isEmpty(), "her listing is unpublished");
        Listing tomb = table.listingByCartridgeId(ExchangeFixtures.id(2)).orElseThrow();
        assertEquals(Optional.of(Tombstones.ERASED_AUTHOR), tomb.gameDef().authorUuid());
        assertEquals(Optional.empty(), tomb.gameDef().authorCredit());
        assertEquals(Optional.empty(), tomb.credit());
        assertTrue(table.listingByCartridgeId(ExchangeFixtures.id(3)).isPresent(), "others' rows stay");
        assertTrue(table.drafts(ExchangeFixtures.ALICE).isEmpty());
        assertEquals(Optional.of(Tombstones.ERASED_AUTHOR),
                table.drafts(ExchangeFixtures.CAROL).get(0).gameDef().authorUuid());
        assertFalse(table.holdsAuthor(ExchangeFixtures.ALICE));
        assertFalse(table.holdsDrafts(ExchangeFixtures.ALICE));
        assertFalse(ExchangeTable.load(table.save()).holdsAuthor(ExchangeFixtures.ALICE));
        assertFalse(table.save().toString().contains(ExchangeFixtures.ALICE.toString()));
    }

    @Test
    void bothStoresAreRegisteredForErasure() {
        List<NeroArcadeData.StoreEraser> erasers = Exchange.erasers();
        assertEquals(List.of("exchange", "drafts"), erasers.stream().map(NeroArcadeData.StoreEraser::name).toList());
        erasers.forEach(e -> e.erase(null, ExchangeFixtures.ALICE));
    }
}
