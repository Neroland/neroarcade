package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.network.chat.FilterMask;
import net.minecraft.server.network.FilteredText;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * 003 §8.2 / §12 items 6–7: only the author or an op publishes or unpublishes; an authorless (or erased-author) game
 * only an op can list; the kill switch refuses everything; titles must be 1..32 clean characters and pass the chat
 * filter (a masked or emptied title is refused, never rewritten).
 */
class ExchangeRightsTest {

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        za.co.neroland.neroarcade.TestBootstrap.ensure();
    }

    @Test
    void publishOnlyByAuthorOrOp() {
        Optional<java.util.UUID> alice = Optional.of(ExchangeFixtures.ALICE);
        assertTrue(ExchangeRules.publish(true, alice, ExchangeFixtures.ALICE, false).isEmpty());
        assertEquals(Optional.of(ExchangeRules.NOT_YOURS),
                ExchangeRules.publish(true, alice, ExchangeFixtures.BOB, false), "someone else's cartridge");
        assertTrue(ExchangeRules.publish(true, alice, ExchangeFixtures.BOB, true).isEmpty(), "an op may");
        assertEquals(Optional.of(ExchangeRules.NO_AUTHOR),
                ExchangeRules.publish(true, Optional.empty(), ExchangeFixtures.BOB, false));
        assertEquals(Optional.of(ExchangeRules.NO_AUTHOR),
                ExchangeRules.publish(true, Optional.of(Tombstones.ERASED_AUTHOR), ExchangeFixtures.BOB, false));
        assertEquals(Optional.of(ExchangeRules.DISABLED), ExchangeRules.publish(false, alice, ExchangeFixtures.ALICE,
                true), "the kill switch beats even an op");
        assertEquals(Tombstones.ERASED_AUTHOR, ExchangeRules.listingAuthor(Optional.empty()),
                "an op-listed authorless game is never credited or paid");
    }

    @Test
    void unpublishByOtherIsRefused() {
        assertEquals(Optional.of(ExchangeRules.UNPUBLISH_NOT_YOURS),
                ExchangeRules.unpublish(true, ExchangeFixtures.ALICE, ExchangeFixtures.BOB, false));
        assertTrue(ExchangeRules.unpublish(true, ExchangeFixtures.ALICE, ExchangeFixtures.ALICE, false).isEmpty());
        assertTrue(ExchangeRules.unpublish(true, ExchangeFixtures.ALICE, ExchangeFixtures.BOB, true).isEmpty());
        assertEquals(Optional.of(ExchangeRules.DISABLED),
                ExchangeRules.unpublish(false, ExchangeFixtures.ALICE, ExchangeFixtures.ALICE, false));
    }

    @Test
    void titleCapAndCleanliness() {
        assertTrue(ExchangeRules.title("Neon Dodge").isEmpty());
        assertTrue(ExchangeRules.title("x".repeat(32)).isEmpty());
        assertEquals(Optional.of(ExchangeRules.TITLE_INVALID), ExchangeRules.title("x".repeat(33)), "33 chars");
        assertEquals(Optional.of(ExchangeRules.TITLE_INVALID), ExchangeRules.title("Neon\u0007Dodge"), "control char");
        assertEquals(Optional.of(ExchangeRules.TITLE_INVALID), ExchangeRules.title("   "), "only whitespace");
        assertEquals(Optional.of(ExchangeRules.TITLE_INVALID), ExchangeRules.title(""), "empty");
        assertEquals(Optional.of(ExchangeRules.TITLE_INVALID), ExchangeRules.title(" padded "), "never rewritten");
    }

    @Test
    void chatFilterRefusal() {
        assertTrue(ExchangeRules.filter(FilteredText.passThrough("Neon Dodge")).isEmpty());
        assertEquals(Optional.of(ExchangeRules.TITLE_BLOCKED),
                ExchangeRules.filter(FilteredText.fullyFiltered("rude words")), "an emptied title");
        assertEquals(Optional.of(ExchangeRules.TITLE_BLOCKED),
                ExchangeRules.filter(new FilteredText("rude word", FilterMask.FULLY_FILTERED)));
    }
}
