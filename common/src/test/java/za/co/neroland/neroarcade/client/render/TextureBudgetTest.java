package za.co.neroland.neroarcade.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.client.ArcadeClientConfig;

/**
 * The screen texture budget (the {@code RenderBudgetTest} pattern): never more than {@link TextureLru#DEFAULT_CAP}
 * live screen textures however many cabinets are in view, the least recently drawn screen gives up its slot first,
 * a slot drawn this tick is never stolen, idle slots are released, and the client's screen range can only shrink
 * the server's.
 */
class TextureBudgetTest {

    @Test
    void neverMoreThanTheCap() {
        TextureLru lru = new TextureLru(TextureLru.DEFAULT_CAP);
        assertEquals(24, lru.capacity(), "004 budget: 20 cabinets + 2 marquees, plus headroom");
        int granted = 0;
        for (long cabinet = 0; cabinet < 100; cabinet++) {
            if (lru.acquire(cabinet, 1) >= 0) {
                granted++;
            }
        }
        assertEquals(24, granted, "100 cabinets in view in one tick: 24 screens, the rest show the ink face");
        assertEquals(24, lru.size());
        assertEquals(0, lru.evictions(), "a slot used this tick is never stolen");
    }

    @Test
    void leastRecentlyDrawnGivesWayFirst() {
        TextureLru lru = new TextureLru(3);
        lru.acquire(10, 1);
        lru.acquire(11, 2);
        lru.acquire(12, 3);
        lru.acquire(10, 4); // 10 drawn again: 11 is now the oldest
        int slot = lru.acquire(13, 5);
        assertTrue(slot >= 0);
        assertEquals(-1, lru.slotOf(11), "the least recently drawn screen lost its slot");
        assertNotEquals(-1, lru.slotOf(10));
        assertEquals(1, lru.evictions());
    }

    @Test
    void idleScreensAreReleased() {
        TextureLru lru = new TextureLru(4);
        lru.acquire(1, 0);
        lru.acquire(2, 0);
        lru.acquire(3, 50);
        List<Integer> freed = new ArrayList<>();
        int n = lru.releaseIdle(50, TextureLru.IDLE_TICKS, freed::add);
        assertEquals(2, n, "asleep, out of range or culled for 2 s: released");
        assertEquals(1, lru.size());
        assertEquals(2, freed.size());
        assertEquals(0, lru.releaseIdle(50, TextureLru.IDLE_TICKS, null));
        assertTrue(lru.release(3) >= 0);
        assertEquals(0, lru.size());
    }

    @Test
    void clientRangeOnlyShrinksTheServers() {
        assertEquals(32, ArcadeClientConfig.effectiveRange(32, 0), "0 = the server's range");
        assertEquals(16, ArcadeClientConfig.effectiveRange(32, 16));
        assertEquals(32, ArcadeClientConfig.effectiveRange(32, 64), "never further than the server allows");
    }
}
