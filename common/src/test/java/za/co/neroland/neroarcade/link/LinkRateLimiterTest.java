package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 007 §3 / §8.6: export 20 a minute and 200 an hour; import 3 a minute and {@code link.importPerHour} an hour; the
 * leaderboard section 30 a minute; refused calls are not counted; the key map is bounded; erasure forgets a player.
 */
class LinkRateLimiterTest {

    @Test
    void shippedRules() {
        LinkRateLimiter limiter = LinkRateLimiter.standard(() -> 10);
        UUID p = LinkFixtures.ALICE;
        for (int i = 0; i < 20; i++) {
            assertEquals(0L, limiter.tryAcquire(p, "export_cartridge", i));
        }
        assertTrue(limiter.tryAcquire(p, "export_cartridge", 20) > 0);
        for (int i = 0; i < 30; i++) {
            assertEquals(0L, limiter.tryAcquire(p, LinkRateLimiter.SECTION_LEADERBOARD, i));
        }
        assertTrue(limiter.tryAcquire(p, LinkRateLimiter.SECTION_LEADERBOARD, 30) > 0);
        assertEquals(0L, limiter.tryAcquire(p, "anything_else", 0), "unknown keys are not limited");
    }

    @Test
    void exportHourlyCap() {
        LinkRateLimiter limiter = LinkRateLimiter.standard(() -> 10);
        long t = 0;
        int accepted = 0;
        for (int i = 0; i < 300; i++) {
            if (limiter.tryAcquire(LinkFixtures.ALICE, "export_cartridge", t) == 0L) {
                accepted++;
            }
            t += 3_100L; // under 20 a minute
        }
        assertEquals(200, accepted, "200 an hour");
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "export_cartridge", 3_600_000L),
                "the first call of the hour has aged out");
    }

    @Test
    void refusedCallsAreNotCountedAndWaitIsExact() {
        LinkRateLimiter limiter = new LinkRateLimiter(a -> List.of(new LinkRateLimiter.Rule(2, 1_000L)));
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "a", 0));
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "a", 100));
        assertEquals(900L, limiter.tryAcquire(LinkFixtures.ALICE, "a", 100));
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire(LinkFixtures.ALICE, "a", 500) > 0);
        }
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "a", 1_000), "the refused calls did not extend it");
    }

    @Test
    void importHourIsLive() {
        int[] perHour = {2};
        LinkRateLimiter limiter = LinkRateLimiter.standard(() -> perHour[0]);
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "import_cartridge", 0));
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "import_cartridge", 61_000));
        assertTrue(limiter.tryAcquire(LinkFixtures.ALICE, "import_cartridge", 122_000) > 0);
        perHour[0] = 10;
        assertEquals(0L, limiter.tryAcquire(LinkFixtures.ALICE, "import_cartridge", 122_000));
    }

    @Test
    void boundedAndErasable() {
        LinkRateLimiter limiter = LinkRateLimiter.standard(() -> 10);
        for (int i = 0; i < LinkRateLimiter.MAX_KEYS + 10; i++) {
            limiter.tryAcquire(new UUID(0L, i), "export_cartridge", 0);
        }
        assertEquals(LinkRateLimiter.MAX_KEYS, limiter.size());
        assertFalse(limiter.holdsAnyFor(new UUID(0L, 0)), "the oldest key went first");
        limiter.tryAcquire(LinkFixtures.ALICE, "export_cartridge", 0);
        assertTrue(limiter.holdsAnyFor(LinkFixtures.ALICE));
        limiter.forget(LinkFixtures.ALICE);
        assertFalse(limiter.holdsAnyFor(LinkFixtures.ALICE));
    }
}
