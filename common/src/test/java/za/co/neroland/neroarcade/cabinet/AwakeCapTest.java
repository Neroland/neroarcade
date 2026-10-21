package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.runtime.Budgets;

/** 002 §9 / R6: {@code runtime.maxAwakeCabinets} (default 32); the 33rd cabinet queues FIFO. */
class AwakeCapTest {

    @Test
    void thirtyThirdCabinetQueuesAndDrainsInOrder() {
        AwakeCabinets cap = new AwakeCabinets(Budgets::maxAwakeCabinets);
        assertEquals(32, Budgets.maxAwakeCabinets(), "the config default");
        List<Object> cabinets = new ArrayList<>();
        for (int i = 0; i < 34; i++) {
            cabinets.add(new Object());
        }
        for (int i = 0; i < 32; i++) {
            assertEquals(AwakeCabinets.Grant.AWAKE, cap.request(cabinets.get(i)));
        }
        assertEquals(AwakeCabinets.Grant.QUEUED, cap.request(cabinets.get(32)));
        assertEquals(AwakeCabinets.Grant.QUEUED, cap.request(cabinets.get(33)));
        assertEquals(1, cap.queuePosition(cabinets.get(32)));
        assertEquals(2, cap.queuePosition(cabinets.get(33)));
        assertFalse(cap.isAwake(cabinets.get(32)));

        cap.release(cabinets.get(5));
        assertTrue(cap.isAwake(cabinets.get(32)), "the head of the queue takes the freed slot");
        assertEquals(1, cap.queuePosition(cabinets.get(33)));
        assertEquals(32, cap.awakeCount());

        cap.release(cabinets.get(33));
        assertEquals(0, cap.queued(), "leaving the queue frees the place");
    }

    @Test
    void requestIsIdempotent() {
        AwakeCabinets cap = new AwakeCabinets(() -> 1);
        Object a = new Object();
        Object b = new Object();
        assertEquals(AwakeCabinets.Grant.AWAKE, cap.request(a));
        assertEquals(AwakeCabinets.Grant.AWAKE, cap.request(a));
        assertEquals(AwakeCabinets.Grant.QUEUED, cap.request(b));
        assertEquals(AwakeCabinets.Grant.QUEUED, cap.request(b));
        assertEquals(1, cap.queued());
    }
}
