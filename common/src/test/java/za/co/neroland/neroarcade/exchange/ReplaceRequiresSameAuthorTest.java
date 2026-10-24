package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * Carried from review 003b (Stage 8 note) into Stage 6: re-publishing a cartridge id that is already listed replaces
 * the listing only when the listing's author is the same ({@code old.authorUuid == author || op}). A cartridge forged
 * in creative with someone else's id and the forger as author cannot take over the victim's listing (author, credit,
 * plays and hearts), and the refusal changes nothing.
 */
class ReplaceRequiresSameAuthorTest {

    @Test
    void anotherAuthorCannotReplaceAListing() {
        ExchangeTable table = new ExchangeTable();
        GameDef alices = ExchangeFixtures.game(1, ExchangeFixtures.ALICE, "Alice");
        Listing original = ExchangeFixtures.publish(table, alices, 1, 200).listing().orElseThrow();
        int version = table.version();

        // Bob forges a cartridge with Alice's id and himself as author
        GameDef forged = ExchangeFixtures.game(ExchangeFixtures.id(1), List.of(), ExchangeFixtures.BOB, "Bob", 9);
        ExchangeTable.PublishResult refused = table.publish(forged, ExchangeFixtures.BOB, Optional.of("Bob"),
                ExchangeFixtures.TAGS, 2, 200, 16, false);
        assertEquals(ExchangeTable.PublishStatus.NOT_OWNER, refused.status());
        assertTrue(refused.listing().isEmpty());
        assertEquals(version, table.version(), "nothing changed");
        Listing still = table.listingByCartridgeId(ExchangeFixtures.id(1)).orElseThrow();
        assertEquals(original, still);
        assertEquals(ExchangeFixtures.ALICE, still.authorUuid());

        // the six-argument overload (no op flag) is not an op either
        assertEquals(ExchangeTable.PublishStatus.NOT_OWNER, table.publish(forged, ExchangeFixtures.BOB,
                Optional.empty(), ExchangeFixtures.TAGS, 3, 200).status());
    }

    @Test
    void theAuthorAndAnOpMayReplace() {
        ExchangeTable table = new ExchangeTable();
        ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, null), 1, 200);
        GameDef revised = ExchangeFixtures.game(ExchangeFixtures.id(1), List.of(), ExchangeFixtures.ALICE, null, 2);
        assertEquals(ExchangeTable.PublishStatus.REPLACED, table.publish(revised, ExchangeFixtures.ALICE,
                Optional.empty(), ExchangeFixtures.TAGS, 2, 200, 16, false).status());
        GameDef opCopy = ExchangeFixtures.game(ExchangeFixtures.id(1), List.of(), ExchangeFixtures.BOB, null, 3);
        assertEquals(ExchangeTable.PublishStatus.REPLACED, table.publish(opCopy, ExchangeFixtures.BOB,
                Optional.empty(), ExchangeFixtures.TAGS, 3, 200, 0, true).status(), "an operator may");
    }
}
