package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 003 §8.1 / §12 item 6: one heart per player per listing per session; the author cannot heart their own game; at most
 * 20 hearts an hour (100 hearts a minute are capped); the count persists, the dedup set does not (it is never saved).
 */
class HeartsDedupTest {

    @Test
    void secondHeartInASessionIsIgnored() {
        ExchangeHearts hearts = new ExchangeHearts();
        assertEquals(ExchangeHearts.Verdict.OK, hearts.tryHeart(ExchangeFixtures.BOB, 1, 0));
        assertEquals(ExchangeHearts.Verdict.ALREADY, hearts.tryHeart(ExchangeFixtures.BOB, 1, 10));
        assertEquals(ExchangeHearts.Verdict.OK, hearts.tryHeart(ExchangeFixtures.CAROL, 1, 10));
        assertTrue(hearts.hearted(ExchangeFixtures.BOB, 1));
    }

    @Test
    void authorCannotHeartTheirOwnGame() {
        assertEquals(Optional.of(ExchangeRules.OWN_HEART),
                ExchangeRules.heart(true, ExchangeFixtures.ALICE, ExchangeFixtures.ALICE));
        assertTrue(ExchangeRules.heart(true, ExchangeFixtures.ALICE, ExchangeFixtures.BOB).isEmpty());
        assertEquals(Optional.of(ExchangeRules.DISABLED),
                ExchangeRules.heart(false, ExchangeFixtures.ALICE, ExchangeFixtures.BOB));
    }

    @Test
    void twentyHeartsAnHour() {
        ExchangeHearts hearts = new ExchangeHearts();
        int ok = 0;
        for (int listing = 0; listing < 100; listing++) {
            if (hearts.tryHeart(ExchangeFixtures.BOB, listing, listing * 600L) == ExchangeHearts.Verdict.OK) {
                ok++;
            }
        }
        assertEquals(ExchangeHearts.PER_HOUR, ok, "100 hearts in a minute: 20 land");
        assertEquals(ExchangeHearts.Verdict.RATE_LIMITED, hearts.tryHeart(ExchangeFixtures.BOB, 500, 60_000));
        assertEquals(ExchangeHearts.Verdict.OK,
                hearts.tryHeart(ExchangeFixtures.BOB, 501, ExchangeHearts.HOUR_MS + 1), "the next hour is free again");
    }

    @Test
    void countPersistsDedupDoesNot() {
        ExchangeTable table = new ExchangeTable();
        Listing l = ExchangeFixtures.publish(table, ExchangeFixtures.game(1, ExchangeFixtures.ALICE, null), 1, 200)
                .listing().orElseThrow();
        ExchangeHearts session = new ExchangeHearts();
        assertEquals(ExchangeHearts.Verdict.OK, session.tryHeart(ExchangeFixtures.BOB, l.listingId(), 0));
        assertEquals(1, table.heart(l.listingId()));

        ExchangeTable reloaded = ExchangeTable.load(table.save());
        assertEquals(1, reloaded.listing(l.listingId()).orElseThrow().hearts(), "the count is saved");
        assertFalse(table.save().toString().contains(ExchangeFixtures.BOB.toString()), "who hearted is never saved");

        session.clear(); // server restart
        assertEquals(0, session.size());
        assertEquals(ExchangeHearts.Verdict.OK, session.tryHeart(ExchangeFixtures.BOB, l.listingId(), 0));
        assertEquals(-1, table.heart(9999), "an unknown listing is not hearted");
    }
}
