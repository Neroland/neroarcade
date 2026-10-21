package za.co.neroland.neroarcade.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The seated client sends its input on every change plus a keep-alive at most 5 times a second, and never more
 * than 20 in any second — under the server's per-seat budget (30 by default, 10 at the lowest), so a well-behaved
 * client never has a packet dropped.
 */
class InputSenderTest {

    private static final SeatInput UP = SeatInput.of(SeatInput.UP, 0, 0);
    private static final SeatInput DOWN = SeatInput.of(SeatInput.DOWN, 0, 0);

    @Test
    void sendsOnChangeAndOnlyThen() {
        InputSender sender = new InputSender();
        assertNotNull(sender.poll(SeatInput.NONE, 1, 0), "the first poll always sends");
        assertNull(sender.poll(SeatInput.NONE, 1, 1), "unchanged: nothing");
        assertEquals(UP, sender.poll(UP, 1, 2), "a change sends at once");
        assertNull(sender.poll(UP, 1, 3));
        assertEquals(UP, sender.poll(UP, 2, 4), "a new match id sends at once");
    }

    @Test
    void keepAliveIsAtMostFiveHertz() {
        InputSender sender = new InputSender();
        int sent = 0;
        for (int tick = 0; tick < 200; tick++) {
            if (sender.poll(UP, 1, tick) != null) {
                sent++;
            }
        }
        assertEquals(50, sent, "10 seconds of a held key: one keep-alive every 4 ticks");
        assertTrue(InputSender.KEEP_ALIVE_HZ <= 5);
    }

    @Test
    void neverMoreThanTwentyInAnySecond() {
        InputSender sender = new InputSender();
        int[] sentAt = new int[1000];
        int n = 0;
        for (int tick = 0; tick < 200; tick++) {
            // a client mashing keys: the input changes on every poll, sometimes twice per tick
            for (int poll = 0; poll < 2; poll++) {
                SeatInput in = ((tick * 2 + poll) & 1) == 0 ? UP : DOWN;
                if (sender.poll(in, 1, tick) != null) {
                    sentAt[n++] = tick;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            int inWindow = 0;
            for (int j = i; j < n && sentAt[j] - sentAt[i] < InputSender.WINDOW_TICKS; j++) {
                inWindow++;
            }
            assertTrue(inWindow <= InputSender.MAX_PER_SECOND, "at most 20 in a second, saw " + inWindow);
            assertTrue(inWindow < 30, "below the server's default budget of 30");
        }
        assertTrue(n >= 150, "the cap does not starve a fast player: " + n);
    }

    @Test
    void resetSendsAgain() {
        InputSender sender = new InputSender();
        sender.poll(UP, 1, 0);
        assertNull(sender.poll(UP, 1, 1));
        sender.reset();
        assertNotNull(sender.poll(UP, 1, 2));
    }

    @Test
    void keysMapToSeatButtonsAndAIsStartBetweenMatches() {
        SeatInput playing = InputSender.toInput(1 | 16, BusPhase.PLAYING);
        assertTrue(playing.has(SeatInput.UP));
        assertTrue(playing.has(SeatInput.A));
        assertTrue(!playing.has(SeatInput.START), "A is not START while playing");
        assertTrue(InputSender.toInput(16, BusPhase.RESULTS).has(SeatInput.START), "A presses START on the results");
        assertTrue(InputSender.toInput(16, BusPhase.LOBBY).has(SeatInput.START), "and in the lobby");
        SeatInput aim = InputSender.toInput((1 << 6) | (1 << 9), BusPhase.PLAYING);
        assertEquals(127, aim.aimX(), "aim right");
        assertEquals(-127, aim.aimY(), "aim up");
        assertEquals(0, InputSender.toInput(0, BusPhase.PLAYING).buttons());
    }
}
