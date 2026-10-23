package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/** 003 §2.2: one seat per player server-wide, across two buses; a cabinet holds one seat. */
class SeatExclusivityAcrossBusesTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void onePlayerCannotHoldSeatsOnTwoBuses() {
        SeatRegistry registry = new SeatRegistry();
        SeatRegistry.SeatKey busOneNode = new SeatRegistry.SeatKey("minecraft:overworld", HostFixtures.NODE.asLong());
        SeatRegistry.SeatKey busTwoNode = new SeatRegistry.SeatKey("minecraft:overworld", 999L);
        assertTrue(registry.claim(HostFixtures.ALICE, busOneNode));
        assertFalse(registry.claim(HostFixtures.ALICE, busTwoNode), "already seated on the first bus");
        assertTrue(registry.claim(HostFixtures.ALICE, busOneNode), "re-claiming the same seat is a no-op");
        registry.release(HostFixtures.ALICE, busOneNode);
        assertTrue(registry.claim(HostFixtures.ALICE, busTwoNode));
        assertEquals(1, registry.size());
    }

    @Test
    void aBusSeatHoldsOnePlayer() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("versus", 2),
                new HostFixtures.CountingWriter(), 2);
        assertTrue(host.bind(1, new Seat(1, HostFixtures.ALICE, 0, HostFixtures.NODE)));
        assertFalse(host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE)), "taken");
        assertFalse(host.bind(2, new Seat(2, HostFixtures.BOB, 0, HostFixtures.NODE)), "this game seats two");
    }
}
